package com.wealthStack.bankstatement

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "banking_operations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_banking_operation_identity", columnNames = ["fingerprint", "occurrence"])
    ]
)
class BankingOperation(
    @Column(nullable = false)
    var date: LocalDate,

    @Column(nullable = false, length = 1000)
    var description: String,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: OperationType,

    @Column(nullable = false)
    var bankName: String,

    @Column(nullable = false, length = 500)
    var account: String,

    /** Display name of the mapped [account], copied from its [AccountMapping]; `null` when unmapped. */
    @Column(length = 500)
    var accountDisplayName: String? = null,

    /**
     * Classification from the editable [Category] dictionary, or `null` (Uncategorized). Set by the
     * user via the UI, or by a *manual* import that names a dictionary category (raw bank parsers
     * never set it). Deliberately excluded from the operation fingerprint (see [OperationFingerprint])
     * so editing it never affects identity/dedup.
     */
    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Column
    var sourceFileName: String? = null,

    /**
     * Content hash of the immutable identity fields (see [OperationFingerprint]). Together with
     * [occurrence] it uniquely identifies an operation so re-imports do not create duplicates.
     */
    @Column(nullable = false, length = 64)
    var fingerprint: String = "",

    /**
     * Disambiguates operations that share a [fingerprint] within the same statement (genuinely
     * identical transactions on the same day). Zero-based, assigned at import time.
     */
    @Column(nullable = false)
    var occurrence: Int = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {

    /**
     * Parse-time carrier for the category name a manual import supplies (CSV `category` column or
     * JSON `category` field). [StatementImporter] resolves it to a [category] dictionary entry,
     * requiring the name to already exist. Never persisted; raw bank parsers leave it null.
     */
    @Transient
    var categoryName: String? = null
}
