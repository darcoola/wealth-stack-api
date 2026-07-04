package com.wealthStack.party

import jakarta.persistence.*
import java.time.Instant

enum class PartyType { PERSON, ORGANIZATION }

/**
 * The owner of all domain data (operations, categories, groups, mappings — each carries a
 * `party_id`). A PERSON party is a user's private workspace, created on first login; an
 * ORGANIZATION party is a shared workspace (household) users join via [PartyMembership].
 * Party id 1 is the well-known pre-multi-user default (see V10 migration).
 */
@Entity
@Table(name = "party")
class Party(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: PartyType,

    @Column(nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
