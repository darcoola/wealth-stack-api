package com.wealthStack.bankstatement

import jakarta.persistence.*

@Entity
@Table(
    name = "account_mappings",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_account_mappings_raw_account", columnNames = ["party_id", "raw_account"])
    ]
)
class AccountMapping(
    @Column(nullable = false, length = 500)
    var rawAccount: String,

    @Column(nullable = false)
    var displayName: String,

    /** Owning party — raw accounts are unique per party, not globally. */
    @Column(name = "party_id", nullable = false)
    var partyId: Long = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
