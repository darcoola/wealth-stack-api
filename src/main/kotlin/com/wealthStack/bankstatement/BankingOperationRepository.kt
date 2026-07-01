package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal

interface BankingOperationRepository : JpaRepository<BankingOperation, Long> {
    fun findAllByAccount(account: String): List<BankingOperation>

    fun findAllByFingerprintIn(fingerprints: Collection<String>): List<BankingOperation>

    fun findAllByCategory(category: Category): List<BankingOperation>

    /**
     * Aggregates every operation into (month, category) buckets, summing amounts as-is (no
     * debit/credit split): spending categories total negative, income categories positive. Carries
     * the category [CategoryMonthSum.categoryType] so callers/charts split by type instead of amount
     * sign. `LEFT JOIN` keeps Uncategorized rows (null category/type).
     * `to_char(date, 'YYYY-MM')` is portable across Postgres and H2.
     */
    @Query(
        """
        SELECT function('to_char', o.date, 'YYYY-MM') AS month,
               c.id AS categoryId, c.name AS categoryName, c.type AS categoryType,
               COALESCE(SUM(o.amount), 0) AS total
        FROM BankingOperation o LEFT JOIN o.category c
        GROUP BY function('to_char', o.date, 'YYYY-MM'), c.id, c.name, c.type
        """
    )
    fun aggregateByMonthAndCategory(): List<CategoryMonthSum>
}

/** Projection for [BankingOperationRepository.aggregateByMonthAndCategory]. */
interface CategoryMonthSum {
    val month: String                // "YYYY-MM"
    val categoryId: Long?            // null = Uncategorized
    val categoryName: String?        // null = Uncategorized
    val categoryType: CategoryType?  // null = Uncategorized
    val total: BigDecimal            // SUM of amount, as-is (signed)
}
