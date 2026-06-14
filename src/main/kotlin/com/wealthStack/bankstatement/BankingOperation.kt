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

    @Column(length = 500)
    var displayName: String? = null,

    @Column(nullable = false)
    var category: String,

    @Column(nullable = false)
    var sourceFileName: String,

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
)
