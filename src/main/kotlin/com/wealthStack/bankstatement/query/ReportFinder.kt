package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.CategoryMonthSum

open class ReportFinder(
    private val repository: BankingOperationRepository
) {

    /**
     * Per (month, category) totals across all history, summed as-is (spending negative, income
     * positive). Each row carries its category type so the frontend can split into separate
     * spending/income charts.
     */
    open fun categoryMonthlyTotals(): List<MonthlyCategoryTotalDto> =
        repository.aggregateByMonthAndCategory().map { it.toDto() }

    private fun CategoryMonthSum.toDto() = MonthlyCategoryTotalDto(
        month = month,
        categoryId = categoryId,
        category = categoryName,
        categoryType = categoryType,
        total = total
    )
}
