package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.AccountMappingRepository

open class AccountMappingFinder(
    private val repository: AccountMappingRepository
) {

    open fun findAll(): List<AccountMappingDto> = repository.findAll()
        .sortedBy { it.displayName.lowercase() }
        .map { AccountMappingDto(id = it.id!!, rawAccount = it.rawAccount, displayName = it.displayName) }
}
