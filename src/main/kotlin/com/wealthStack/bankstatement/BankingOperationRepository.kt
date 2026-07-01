package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal

interface BankingOperationRepository : JpaRepository<BankingOperation, Long> {
    fun findAllByAccount(account: String): List<BankingOperation>

    fun findAllByFingerprintIn(fingerprints: Collection<String>): List<BankingOperation>

    fun findAllByCategory(category: Category): List<BankingOperation>

    /**
     * Aggregates every operation into (month, category) buckets, splitting credits and debits so the
     * caller can derive net / spendings / income. `LEFT JOIN` keeps Uncategorized rows (null category).
     * `to_char(date, 'YYYY-MM')` is portable across Postgres and H2.
     */
    @Query(
        """
        SELECT function('to_char', o.date, 'YYYY-MM') AS month,
               c.id AS categoryId, c.name AS categoryName,
               COALESCE(SUM(CASE WHEN o.amount >= 0 THEN o.amount ELSE 0 END), 0) AS creditSum,
               COALESCE(SUM(CASE WHEN o.amount <  0 THEN o.amount ELSE 0 END), 0) AS debitSum
        FROM BankingOperation o LEFT JOIN o.category c
        GROUP BY function('to_char', o.date, 'YYYY-MM'), c.id, c.name
        """
    )
    fun aggregateByMonthAndCategory(): List<CategoryMonthSum>
}

/** Projection for [BankingOperationRepository.aggregateByMonthAndCategory]. */
interface CategoryMonthSum {
    val month: String          // "YYYY-MM"
    val categoryId: Long?       // null = Uncategorized
    val categoryName: String?   // null = Uncategorized
    val creditSum: BigDecimal   // SUM of amount >= 0
    val debitSum: BigDecimal    // SUM of amount < 0 (negative)
}
