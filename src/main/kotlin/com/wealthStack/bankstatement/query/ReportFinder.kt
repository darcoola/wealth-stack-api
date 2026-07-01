package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.CategoryMonthSum

/** How the signed amounts are collapsed into a single per-bucket total. */
enum class AmountMode {
    /** Net signed sum (credits minus debits). May be negative. */
    ALL,

    /** Debit magnitudes only (money spent), rendered positive. */
    SPENDINGS,

    /** Credits only (money in). */
    INCOME;

    companion object {
        /** Case-insensitive lookup for the `mode` query param; unknown/blank falls back to [ALL]. */
        fun fromParam(value: String?): AmountMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALL
    }
}

open class ReportFinder(
    private val repository: BankingOperationRepository
) {

    /** Per (month, category) totals across all history, with each total collapsed per [mode]. */
    open fun categoryMonthlyTotals(mode: AmountMode): List<MonthlyCategoryTotalDto> =
        repository.aggregateByMonthAndCategory().map { it.toDto(mode) }

    private fun CategoryMonthSum.toDto(mode: AmountMode) = MonthlyCategoryTotalDto(
        month = month,
        categoryId = categoryId,
        category = categoryName,
        total = when (mode) {
            AmountMode.ALL -> creditSum + debitSum
            AmountMode.SPENDINGS -> debitSum.negate()
            AmountMode.INCOME -> creditSum
        }
    )
}
