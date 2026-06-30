package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.BankingOperationRepository

open class BankingOperationFinder(
    private val repository: BankingOperationRepository
) {

    open fun findAll(): List<OperationDto> = repository.findAll().map { it.toDto() }
}

internal fun BankingOperation.toDto() = OperationDto(
    id = id!!,
    date = date,
    description = description,
    account = account,
    accountDisplayName = accountDisplayName ?: account,
    amount = amount,
    categoryId = category?.id,
    category = category?.name
)
