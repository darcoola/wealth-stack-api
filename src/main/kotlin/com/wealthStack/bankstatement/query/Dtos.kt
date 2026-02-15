package com.wealthStack.bankstatement.query

import java.math.BigDecimal
import java.time.LocalDate

data class OperationDto(
    val date: LocalDate,
    val description: String,
    val account: String,
    val displayName: String,
    val amount: BigDecimal,
    val category: String
)

data class AccountMappingDto(
    val rawAccount: String,
    val displayName: String
)
