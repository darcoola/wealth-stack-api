package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.toDto
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

open class StatementImporter(
    private val parserFactory: StatementParserFactory,
    private val repository: BankingOperationRepository,
    private val accountMappingRepository: AccountMappingRepository,
    private val categoryRepository: CategoryRepository
) {

    @Transactional
    open fun importStatement(bankName: String, fileName: String, content: ByteArray): ImportResult {
        val parser = parserFactory.getParser(bankName)
        val operations = parser.parse(String(content, parser.charset), fileName)
        return persist(operations, bankName, fileName)
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

    private fun persist(operations: List<BankingOperation>, bankName: String, fileName: String?): ImportResult {
        applyAccountMappings(operations)
        resolveCategories(operations)
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

    private fun applyAccountMappings(operations: List<BankingOperation>) {
        val mappings = accountMappingRepository.findAll().associate { it.rawAccount to it.displayName }
        operations.forEach { op -> mappings[op.account]?.let { op.displayName = it } }
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
        displayName = incoming.displayName
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
        displayName = displayName,
        sourceFileName = sourceFileName
    ).apply { categoryName = this@toEntity.category }
}
