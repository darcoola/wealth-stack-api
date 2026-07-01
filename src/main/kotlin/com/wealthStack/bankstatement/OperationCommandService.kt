package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of individual operations beyond category assignment. Currently owns bulk deletion;
 * category (re)assignment lives in [CategoryService] since it touches the category dictionary.
 */
open class OperationCommandService(
    private val bankingOperationRepository: BankingOperationRepository
) {

    /** Permanently removes the given operations. Unknown ids are silently ignored. */
    @Transactional
    open fun deleteAll(operationIds: List<Long>) {
        bankingOperationRepository.deleteAllById(operationIds)
    }

    /**
     * Sets (or clears, with a blank value) the free-text [BankingOperation.additionalInfo] note on a
     * single operation — the user can annotate any row, including bank-imported ones that carry none.
     */
    @Transactional
    open fun updateAdditionalInfo(operationId: Long, additionalInfo: String?): BankingOperation {
        val operation = bankingOperationRepository.findById(operationId)
            .orElseThrow { IllegalArgumentException("Operation $operationId not found") }
        operation.additionalInfo = additionalInfo?.trim()?.takeIf { it.isNotEmpty() }
        return bankingOperationRepository.save(operation)
    }
}
