package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.toDto
import org.springframework.transaction.annotation.Transactional

open class StatementImporter(
    private val parserFactory: StatementParserFactory,
    private val repository: BankingOperationRepository,
    private val accountMappingRepository: AccountMappingRepository
) {

    @Transactional
    open fun importStatement(bankName: String, fileName: String, content: ByteArray): ImportResult {
        val parser = parserFactory.getParser(bankName)
        val operations = parser.parse(String(content, parser.charset), fileName)

        applyAccountMappings(operations)
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

        return ImportResult(
            message = "Imported $imported and overwrote $overwritten operations from $fileName",
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

    /** Copies the non-identity fields onto an existing row when overwriting a duplicate. */
    private fun BankingOperation.overwriteWith(incoming: BankingOperation) {
        category = incoming.category
        displayName = incoming.displayName
        sourceFileName = incoming.sourceFileName
    }
}
