package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.CategoryMonthSum

open class ReportFinder(
    private val repository: BankingOperationRepository
) {

    /**
     * Per (month, category) totals across the party's whole history, summed as-is (spending
     * negative, income positive). Each row carries its category's group so the frontend can split
     * into one section per group.
     */
    open fun categoryMonthlyTotals(partyId: Long): List<MonthlyCategoryTotalDto> =
        repository.aggregateByMonthAndCategory(partyId).map { it.toDto() }

    private fun CategoryMonthSum.toDto() = MonthlyCategoryTotalDto(
        month = month,
        categoryId = categoryId,
        category = categoryName,
        groupId = groupId,
        groupName = groupName,
        total = total
    )
}
