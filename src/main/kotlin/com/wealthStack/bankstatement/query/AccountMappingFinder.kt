package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.AccountMappingRepository

open class AccountMappingFinder(
    private val repository: AccountMappingRepository
) {

    open fun findAll(): List<AccountMappingDto> = repository.findAll().map {
        AccountMappingDto(rawAccount = it.rawAccount, displayName = it.displayName)
    }
}
