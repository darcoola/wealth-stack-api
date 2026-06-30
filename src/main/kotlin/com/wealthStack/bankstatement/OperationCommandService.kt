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
}
