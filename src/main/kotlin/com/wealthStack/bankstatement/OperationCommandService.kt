package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of individual operations beyond category assignment. Currently owns bulk deletion;
 * category (re)assignment lives in [CategoryService] since it touches the category dictionary.
 * All commands are scoped to the acting party — another party's operations are invisible to them.
 */
open class OperationCommandService(
    private val bankingOperationRepository: BankingOperationRepository
) {

    /** Permanently removes the given operations of the party. Unknown/foreign ids are silently ignored. */
    @Transactional
    open fun deleteAll(partyId: Long, operationIds: List<Long>) {
        val owned = bankingOperationRepository.findAllById(operationIds).filter { it.partyId == partyId }
        bankingOperationRepository.deleteAll(owned)
    }

    /**
     * Permanently removes every operation of the party. Used by the Administration page to wipe the
     * ledger clean (e.g. to start over); account mappings, categories and groups are left intact.
     * Returns the number of operations that were deleted.
     */
    @Transactional
    open fun deleteEverything(partyId: Long): Long {
        val count = bankingOperationRepository.countByPartyId(partyId)
        bankingOperationRepository.deleteAllByPartyId(partyId)
        return count
    }

    /**
     * Sets (or clears, with a blank value) the free-text [BankingOperation.additionalInfo] note on a
     * single operation — the user can annotate any row, including bank-imported ones that carry none.
     */
    @Transactional
    open fun updateAdditionalInfo(partyId: Long, operationId: Long, additionalInfo: String?): BankingOperation {
        val operation = bankingOperationRepository.findById(operationId)
            .filter { it.partyId == partyId }
            .orElseThrow { IllegalArgumentException("Operation $operationId not found") }
        operation.additionalInfo = additionalInfo?.trim()?.takeIf { it.isNotEmpty() }
        return bankingOperationRepository.save(operation)
    }
}
