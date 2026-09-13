package com.wealthStack.bankstatement

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A single operation the user types into the UI's Add-operation form — cash spending or income that
 * no bank statement will ever carry. The bank/account are not part of the payload: hand-entered rows
 * are always booked on the party's cash account (see [StatementImporter.addCashOperation]).
 *
 * Unlike [ManualOperationsRequest] this is not an import: it never folds onto an existing row.
 */
data class NewOperationRequest(
    val date: LocalDate,
    val description: String,
    /** Signed: negative = spending, positive = income (the form flips the sign, not the user). */
    val amount: BigDecimal,
    /** Optional dictionary category; when absent the row is auto-categorized like an imported one. */
    val categoryId: Long? = null,
    val additionalInfo: String? = null,
    /**
     * Confirms a save that an identical operation already exists for (same date, amount and
     * description). Without it such a request is rejected with 409 so the UI can ask the user
     * whether this really is a second, genuine operation rather than a double-submit.
     *
     * Nullable rather than a defaulted primitive: Jackson maps the absent field to `null`, and a
     * `Boolean` parameter would reject it outright.
     */
    val force: Boolean? = null
)

/**
 * Thrown when a hand-entered operation matches one already recorded (same date, amount and
 * description) and the user has not confirmed it is a genuine second operation. Carries the matches
 * so the UI can show what it found.
 */
class DuplicateOperationException(val existing: List<BankingOperation>) :
    RuntimeException("An identical operation is already recorded")
