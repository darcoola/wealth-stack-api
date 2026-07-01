package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.CategoryType
import java.math.BigDecimal
import java.time.LocalDate

data class OperationDto(
    val id: Long,
    val date: LocalDate,
    val description: String,
    val account: String,
    val accountDisplayName: String,
    val amount: BigDecimal,
    val additionalInfo: String?,
    val categoryId: Long?,
    val category: String?
)

data class AccountMappingDto(
    val id: Long,
    val rawAccount: String,
    val displayName: String
)

data class CategoryDto(
    val id: Long,
    val name: String,
    val type: CategoryType
)

data class MonthlyCategoryTotalDto(
    val month: String,             // "YYYY-MM"
    val categoryId: Long?,         // null = Uncategorized
    val category: String?,         // null -> frontend shows "Uncategorized"
    val categoryType: CategoryType?, // null = Uncategorized
    val total: BigDecimal          // SUM of amount, as-is (signed)
)
