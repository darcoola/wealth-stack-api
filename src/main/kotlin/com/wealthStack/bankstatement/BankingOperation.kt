package com.wealthStack.bankstatement

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "banking_operations")
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
