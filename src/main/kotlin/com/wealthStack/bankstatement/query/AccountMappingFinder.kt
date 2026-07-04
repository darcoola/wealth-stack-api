package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.AccountMappingRepository

open class AccountMappingFinder(
    private val repository: AccountMappingRepository
) {

    open fun findAll(partyId: Long): List<AccountMappingDto> = repository.findAllByPartyId(partyId)
        .sortedBy { it.displayName.lowercase() }
        .map { AccountMappingDto(id = it.id!!, rawAccount = it.rawAccount, displayName = it.displayName) }
}
