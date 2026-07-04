package com.wealthStack.party

import jakarta.persistence.*
import java.time.Instant

/**
 * Local mirror of a Keycloak identity, keyed by the JWT `sub` claim. Exists only so memberships
 * and future references have a stable FK target — authentication, passwords, and the approval
 * role all live in Keycloak. Rows are created just-in-time on first authenticated request
 * (see [UserProvisioningService]).
 */
@Entity
@Table(name = "app_user")
class AppUser(
    @Column(name = "keycloak_sub", nullable = false, unique = true, length = 36)
    var keycloakSub: String,

    @Column(nullable = false, unique = true, length = 320)
    var email: String,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Column(name = "personal_party_id", nullable = false)
    var personalPartyId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
