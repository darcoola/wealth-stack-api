package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.toDto
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

open class StatementImporter(
    private val parserFactory: StatementParserFactory,
    private val repository: BankingOperationRepository,
    private val accountMappingRepository: AccountMappingRepository,
    private val categoryRepository: CategoryRepository,
    private val autoCategorizationService: com.wealthStack.bankstatement.search.AutoCategorizationService
) {

    /**
     * Imports a bank CSV into the given party's ledger. When [bankName] is null/blank the parser is
     * auto-detected from the file content ([StatementParserFactory.detectParser]); an unrecognized
     * or ambiguous file fails the import (HTTP 400).
     */
    @Transactional
    open fun importStatement(partyId: Long, bankName: String?, fileName: String, content: ByteArray): ImportResult {
        val parser = if (bankName.isNullOrBlank()) {
            parserFactory.detectParser(content)
        } else {
            parserFactory.getParser(bankName)
        }
        val operations = parser.parse(String(content, parser.charset), fileName)
        return persist(partyId, operations, parser.bankName, fileName)
    }

    /**
     * Ingests already-prepared operation rows supplied as JSON instead of a bank CSV — used for
     * historical data or banks without a parser. Rows go through the same mapping, fingerprinting
     * and duplicate-overwrite pipeline as parsed statements.
     */
    @Transactional
    open fun importOperations(partyId: Long, request: ManualOperationsRequest): ImportResult {
        require(request.operations.isNotEmpty()) { "At least one operation is required" }
        val source = request.source?.takeIf { it.isNotBlank() }
        val operations = request.operations.map { it.toEntity(request.bankName, source) }
        return persist(partyId, operations, request.bankName, source)
    }

    private fun persist(
        partyId: Long,
        operations: List<BankingOperation>,
        bankName: String,
        fileName: String?
    ): ImportResult {
        // Parsers know nothing about parties; stamp ownership before any lookup or persistence.
        operations.forEach { it.partyId = partyId }

        applyAccountMappings(partyId, operations)
        resolveCategories(partyId, operations)

        operations.filter { it.category == null }.forEach { op ->
            val predicted = autoCategorizationService.predictCategory(op)
            if (predicted != null) {
                op.category = predicted
                op.needsVerification = true
            }
        }

        assignFingerprints(operations)

        // Existing rows that could collide with this batch, keyed by their (fingerprint, occurrence)
        // identity so a re-import maps onto the same physical row instead of inserting a duplicate.
        // Scoped to the party: identical statements imported by two parties never collide.
        val existingByIdentity = repository
            .findAllByPartyIdAndFingerprintIn(partyId, operations.map { it.fingerprint }.toSet())
            .associateBy { it.fingerprint to it.occurrence }

        var imported = 0
        var overwritten = 0
        val persisted = operations.map { incoming ->
            val existing = existingByIdentity[incoming.fingerprint to incoming.occurrence]
            if (existing == null) {
                imported++
                incoming
            } else {
                overwritten++
                existing.overwriteWith(incoming)
                existing
            }
        }

        repository.saveAll(persisted)

        autoCategorizationService.indexOperations(persisted)

        val origin = fileName?.let { " from $it" } ?: ""
        return ImportResult(
            message = "Imported $imported and overwrote $overwritten operations$origin",
            bankName = bankName,
            fileName = fileName,
            operationsImported = imported,
            operationsOverwritten = overwritten,
            operations = persisted.map { it.toDto() }
        )
    }

    /**
     * Ensures every imported operation is connected to an [AccountMapping] of the importing party.
     * Existing mappings are applied as before; any raw account seen in this batch that has no
     * mapping yet gets one created on the fly with its display name defaulting to the raw account
     * (the user can rename it later on the Accounts page). The denormalized
     * [BankingOperation.accountDisplayName] is then set from the mapping for every operation.
     */
    private fun applyAccountMappings(partyId: Long, operations: List<BankingOperation>) {
        val mappings = accountMappingRepository.findAllByPartyId(partyId)
            .associateByTo(HashMap()) { it.rawAccount }

        val missing = operations.map { it.account }.toSet().filter { it !in mappings }
        if (missing.isNotEmpty()) {
            accountMappingRepository
                .saveAll(missing.map { AccountMapping(rawAccount = it, displayName = it, partyId = partyId) })
                .forEach { mappings[it.rawAccount] = it }
        }

        operations.forEach { op -> mappings[op.account]?.let { op.accountDisplayName = it.displayName } }
    }

    /**
     * Resolves the category name a manual import carries (see [BankingOperation.categoryName]) to
     * the party's dictionary entry, requiring it to already exist — an unknown name fails the whole
     * import (HTTP 400). Rows without a name (every raw-bank row) are left Uncategorized.
     */
    private fun resolveCategories(partyId: Long, operations: List<BankingOperation>) {
        val resolved = HashMap<String, Category>()
        operations.forEach { op ->
            val name = op.categoryName?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            op.category = resolved.getOrPut(name) {
                categoryRepository.findByPartyIdAndName(partyId, name)
                    ?: throw IllegalArgumentException(
                        "Unknown category '$name'. Create it in the dictionary before importing."
                    )
            }
        }
    }

    /**
     * Sets the content fingerprint on every operation and a zero-based occurrence index that
     * disambiguates operations sharing a fingerprint within this statement (genuinely identical
     * transactions on the same day). File order is stable, so a re-import assigns the same
     * indices and folds onto the existing rows.
     */
    private fun assignFingerprints(operations: List<BankingOperation>) {
        val seen = HashMap<String, Int>()
        operations.forEach { op ->
            op.fingerprint = OperationFingerprint.of(op)
            op.occurrence = seen.merge(op.fingerprint, 1) { current, _ -> current + 1 }!! - 1
        }
    }

    /**
     * Copies the non-identity fields onto an existing row when overwriting a duplicate. `category`
     * is copied only when the incoming row carries one: a manual import that names a category wins
     * (the file is the source of truth), while a raw-bank re-import (which never carries a category)
     * preserves whatever the user assigned in the UI.
     */
    private fun BankingOperation.overwriteWith(incoming: BankingOperation) {
        accountDisplayName = incoming.accountDisplayName
        additionalInfo = incoming.additionalInfo
        sourceFileName = incoming.sourceFileName
        incoming.category?.let { category = it }
    }

    /** Builds an entity from a JSON-supplied row, deriving the type from the amount sign. */
    private fun ManualOperation.toEntity(bankName: String, sourceFileName: String?) = BankingOperation(
        date = date,
        description = description,
        amount = amount,
        type = if (amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT,
        bankName = bankName,
        account = account,
        accountDisplayName = accountDisplayName,
        additionalInfo = additionalInfo,
        sourceFileName = sourceFileName
    ).apply { categoryName = this@toEntity.category }
}
