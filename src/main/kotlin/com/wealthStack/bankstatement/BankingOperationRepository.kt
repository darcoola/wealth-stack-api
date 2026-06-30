package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface BankingOperationRepository : JpaRepository<BankingOperation, Long> {
    fun findAllByAccount(account: String): List<BankingOperation>

    fun findAllByFingerprintIn(fingerprints: Collection<String>): List<BankingOperation>

    fun findAllByCategory(category: Category): List<BankingOperation>
}
