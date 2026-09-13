package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface AccountMappingRepository : JpaRepository<AccountMapping, Long> {
    fun findByRawAccount(rawAccount: String): AccountMapping?
}
