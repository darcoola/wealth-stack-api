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
    val categoryId: Long?,
    val category: String?
)

data class AccountMappingDto(
    val rawAccount: String,
    val displayName: String
)

data class CategoryDto(
    val id: Long,
    val name: String
)
