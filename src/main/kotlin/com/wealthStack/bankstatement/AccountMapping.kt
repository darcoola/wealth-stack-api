package com.wealthStack.bankstatement

import jakarta.persistence.*

@Entity
@Table(name = "account_mappings")
class AccountMapping(
    @Column(nullable = false, unique = true, length = 500)
    var rawAccount: String,

    @Column(nullable = false)
    var displayName: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
