package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface AccountMappingRepository : JpaRepository<AccountMapping, Long> {
    fun findByPartyIdAndRawAccount(partyId: Long, rawAccount: String): AccountMapping?

    fun findAllByPartyId(partyId: Long): List<AccountMapping>
}
