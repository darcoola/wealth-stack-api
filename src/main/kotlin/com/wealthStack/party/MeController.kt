package com.wealthStack.party

import com.wealthStack.security.SecurityConfig
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Who am I, am I approved, and which parties can I act in. This is the only data endpoint an
 * authenticated-but-unapproved user may call (see [com.wealthStack.security.SecurityConfig]):
 * the SPA uses `approved = false` to show the "pending approval" page. Provisioning is deferred
 * until approval so rejected sign-ups never leave rows behind.
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val provisioningService: UserProvisioningService,
    private val partyRepository: PartyRepository,
    private val membershipRepository: PartyMembershipRepository,
) {

    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse {
        val realmAccess = jwt.claims["realm_access"] as? Map<*, *>
        val roles = (realmAccess?.get("roles") as? Collection<*>).orEmpty()
        val approved = SecurityConfig.REQUIRED_ROLE in roles

        if (!approved) {
            return MeResponse(
                email = jwt.getClaimAsString("email") ?: "",
                displayName = jwt.getClaimAsString("name") ?: "",
                approved = false,
                personalPartyId = null,
                parties = emptyList(),
            )
        }

        val user = provisioningService.getOrProvision(
            jwt.subject,
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
        )
        val memberships = membershipRepository.findAllByUserId(user.id!!)
        val partiesById = partyRepository.findAllById(memberships.map { it.partyId }).associateBy { it.id }
        val parties = memberships.mapNotNull { membership ->
            partiesById[membership.partyId]?.let {
                PartySummary(it.id!!, it.name, it.type, membership.role)
            }
        }.sortedBy { it.id }
        return MeResponse(user.email, user.displayName, true, user.personalPartyId, parties)
    }
}

data class MeResponse(
    val email: String,
    val displayName: String,
    val approved: Boolean,
    val personalPartyId: Long?,
    val parties: List<PartySummary>,
)

data class PartySummary(
    val id: Long,
    val name: String,
    val type: PartyType,
    val role: PartyRole,
)
