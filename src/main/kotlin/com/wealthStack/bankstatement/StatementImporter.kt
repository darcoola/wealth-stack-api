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

    companion object {
        /** Bank/account recorded for hand-entered cash rows — there is no bank and no account number. */
        const val CASH_BANK_NAME = "cash"
        const val CASH_ACCOUNT = "Cash"
    }

    /**
     * Imports a bank CSV. When [bankName] is null/blank the parser is auto-detected from the file
     * content ([StatementParserFactory.detectParser]); an unrecognized or ambiguous file fails the
     * import (HTTP 400).
     */
    @Transactional
    open fun importStatement(bankName: String?, fileName: String, content: ByteArray): ImportResult {
        val parser = if (bankName.isNullOrBlank()) {
            parserFactory.detectParser(content)
        } else {
            parserFactory.getParser(bankName)
        }
        val operations = parser.parse(String(content, parser.charset), fileName)
        return persist(operations, parser.bankName, fileName)
    }

    /**
     * Ingests already-prepared operation rows supplied as JSON instead of a bank CSV — used for
     * historical data or banks without a parser. Rows go through the same mapping, fingerprinting
     * and duplicate-overwrite pipeline as parsed statements.
     */
    @Transactional
    open fun importOperations(request: ManualOperationsRequest): ImportResult {
        require(request.operations.isNotEmpty()) { "At least one operation is required" }
        val source = request.source?.takeIf { it.isNotBlank() }
        val operations = request.operations.map { it.toEntity(request.bankName, source) }
        return persist(operations, request.bankName, source)
    }

    /**
     * Records a single hand-entered cash operation. Deliberately *not* an import: it never folds onto
     * a fingerprint-identical row, because two identical cash spends on the same day (two ×12 PLN
     * coffees) can be two real operations — the occurrence index just continues past what is stored.
     *
     * Two identical entries are, however, far more often a double-submit than a genuine second spend,
     * so an unforced request that matches an existing operation is rejected with
     * [DuplicateOperationException] (HTTP 409) carrying the matches; the UI asks the user and re-sends
     * with `force = true` if they confirm.
     *
     * Everything else matches the import pipeline: the cash account gets its [AccountMapping]
     * auto-created on first use, an operation the user leaves uncategorized is offered a prediction
     * (flagged `needsVerification`), and the row is indexed to inform future predictions.
     */
    @Transactional
    open fun addCashOperation(request: NewOperationRequest): BankingOperation {
        val description = request.description.trim()
        require(description.isNotEmpty()) { "Description is required" }

        val operation = BankingOperation(
            date = request.date,
            description = description,
            amount = request.amount,
            type = if (request.amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT,
            bankName = CASH_BANK_NAME,
            account = CASH_ACCOUNT,
            additionalInfo = request.additionalInfo?.trim()?.takeIf { it.isNotEmpty() }
        )
        operation.fingerprint = OperationFingerprint.of(operation)

        // Identity fields only (date, amount, description) — the note and category are not part of
        // the fingerprint, so re-typing the same spend with a different note still counts as a match.
        val existing = repository.findAllByFingerprintIn(setOf(operation.fingerprint))
        if (existing.isNotEmpty() && request.force != true) {
            throw DuplicateOperationException(existing)
        }
        operation.occurrence = existing.maxOfOrNull { it.occurrence }?.plus(1) ?: 0

        applyAccountMappings(listOf(operation))

        if (request.categoryId != null) {
            operation.category = categoryRepository.findById(request.categoryId)
                .orElseThrow { IllegalArgumentException("Unknown category ${request.categoryId}") }
        } else {
            autoCategorizationService.predictCategory(operation)?.let {
                operation.category = it
                operation.needsVerification = true
            }
        }

        val saved = repository.save(operation)
        autoCategorizationService.indexOperations(listOf(saved))
        return saved
    }

    private fun persist(operations: List<BankingOperation>, bankName: String, fileName: String?): ImportResult {
        applyAccountMappings(operations)
        resolveCategories(operations)

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
        val existingByIdentity = repository
            .findAllByFingerprintIn(operations.map { it.fingerprint }.toSet())
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
     * Ensures every imported operation is connected to an [AccountMapping]. Existing mappings are
     * applied as before; any raw account seen in this batch that has no mapping yet gets one created
     * on the fly with its display name defaulting to the raw account (the user can rename it later on
     * the Accounts page). The denormalized [BankingOperation.accountDisplayName] is then set from the
     * mapping for every operation.
     */
    private fun applyAccountMappings(operations: List<BankingOperation>) {
        val mappings = accountMappingRepository.findAll()
            .associateByTo(HashMap()) { it.rawAccount }

        val missing = operations.map { it.account }.toSet().filter { it !in mappings }
        if (missing.isNotEmpty()) {
            accountMappingRepository
                .saveAll(missing.map { AccountMapping(rawAccount = it, displayName = it) })
                .forEach { mappings[it.rawAccount] = it }
        }

        operations.forEach { op -> mappings[op.account]?.let { op.accountDisplayName = it.displayName } }
    }

    /**
     * Resolves the category name a manual import carries (see [BankingOperation.categoryName]) to a
     * dictionary entry, requiring it to already exist — an unknown name fails the whole import
     * (HTTP 400). Rows without a name (every raw-bank row) are left Uncategorized.
     */
    private fun resolveCategories(operations: List<BankingOperation>) {
        val resolved = HashMap<String, Category>()
        operations.forEach { op ->
            val name = op.categoryName?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            op.category = resolved.getOrPut(name) {
                categoryRepository.findByName(name)
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
