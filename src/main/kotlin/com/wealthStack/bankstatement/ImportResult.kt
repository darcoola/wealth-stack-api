package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.query.OperationDto

data class ImportResult(
    val message: String,
    val bankName: String,
    val fileName: String,
    val operationsImported: Int,
    val operations: List<OperationDto>
)
