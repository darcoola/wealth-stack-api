package com.wealthStack.bankstatement.query

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
    val category: String?,
    val needsVerification: Boolean
)

data class AccountMappingDto(
    val id: Long,
    val rawAccount: String,
    val displayName: String
)

data class CategoryDto(
    val id: Long,
    val name: String,
    val groupId: Long?,            // null = Ungrouped
    val groupName: String?         // null = Ungrouped
)

data class CategoryGroupDto(
    val id: Long,
    val name: String
)

data class MonthlyCategoryTotalDto(
    val month: String,             // "YYYY-MM"
    val categoryId: Long?,         // null = Uncategorized
    val category: String?,         // null -> frontend shows "Uncategorized"
    val groupId: Long?,            // null = Ungrouped (or Uncategorized)
    val groupName: String?,        // null = Ungrouped (or Uncategorized)
    val total: BigDecimal          // SUM of amount, as-is (signed)
)
