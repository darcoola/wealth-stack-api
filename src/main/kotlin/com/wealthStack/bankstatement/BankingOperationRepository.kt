package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal

import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface BankingOperationRepository : JpaRepository<BankingOperation, Long>, JpaSpecificationExecutor<BankingOperation> {
    fun findAllByAccount(account: String): List<BankingOperation>

    fun findAllByFingerprintIn(fingerprints: Collection<String>): List<BankingOperation>

    fun findAllByCategory(category: Category): List<BankingOperation>

    /**
     * Aggregates every operation into (month, category) buckets, summing amounts as-is (no
     * debit/credit split): spending categories total negative, income categories positive. Carries
     * the category's [CategoryMonthSum.groupId]/[CategoryMonthSum.groupName] so callers/charts split
     * into one section per group instead of by amount sign. `LEFT JOIN` keeps Uncategorized rows
     * (null category) and Ungrouped categories (null group).
     * `to_char(date, 'YYYY-MM')` is portable across Postgres and H2.
     */
    @Query(
        """
        SELECT function('to_char', o.date, 'YYYY-MM') AS month,
               c.id AS categoryId, c.name AS categoryName,
               g.id AS groupId, g.name AS groupName,
               COALESCE(SUM(o.amount), 0) AS total
        FROM BankingOperation o LEFT JOIN o.category c LEFT JOIN c.group g
        GROUP BY function('to_char', o.date, 'YYYY-MM'), c.id, c.name, g.id, g.name
        """
    )
    fun aggregateByMonthAndCategory(): List<CategoryMonthSum>
}

/** Projection for [BankingOperationRepository.aggregateByMonthAndCategory]. */
interface CategoryMonthSum {
    val month: String                // "YYYY-MM"
    val categoryId: Long?            // null = Uncategorized
    val categoryName: String?        // null = Uncategorized
    val groupId: Long?               // null = Ungrouped (or Uncategorized)
    val groupName: String?           // null = Ungrouped (or Uncategorized)
    val total: BigDecimal            // SUM of amount, as-is (signed)
}
