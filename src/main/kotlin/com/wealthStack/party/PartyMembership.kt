package com.wealthStack.party

import jakarta.persistence.*

enum class PartyRole { OWNER, MEMBER }

/**
 * Connects a user to a party they may act in. Every user OWNs their personal party; organization
 * parties have one or more OWNERs (may manage members) and any number of MEMBERs.
 */
@Entity
@Table(
    name = "party_membership",
    uniqueConstraints = [UniqueConstraint(name = "uk_party_membership", columnNames = ["party_id", "user_id"])]
)
class PartyMembership(
    @Column(name = "party_id", nullable = false)
    var partyId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: PartyRole,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
