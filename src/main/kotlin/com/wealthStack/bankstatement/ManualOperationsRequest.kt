package com.wealthStack.bankstatement

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Payload for ingesting already-prepared operation rows as JSON (e.g. historical records or
 * statements from a bank that has no parser yet). [bankName] applies to every row; [source] is
 * optional and, when present, recorded as the operation's `sourceFileName` provenance.
 */
data class ManualOperationsRequest(
    val bankName: String,
    val source: String? = null,
    val operations: List<ManualOperation>
)

data class ManualOperation(
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val account: String,
    val category: String? = null,
    val displayName: String? = null
)
