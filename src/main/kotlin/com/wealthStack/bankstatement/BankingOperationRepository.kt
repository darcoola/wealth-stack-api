package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface BankingOperationRepository : JpaRepository<BankingOperation, Long> {
    fun findAllByAccount(account: String): List<BankingOperation>
}
