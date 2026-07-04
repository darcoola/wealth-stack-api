package com.wealthStack.party

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.support.TransactionTemplate

/**
 * Just-in-time user provisioning: the first authenticated request creates the [AppUser] row, a
 * personal PERSON [Party], and the OWNER [PartyMembership] from JWT claims. No sign-up endpoint
 * exists — signing in through Keycloak (e.g. via Google) IS the sign-up.
 *
 * Bootstrap adoption: data imported before multi-user support belongs to the well-known party
 * id 1 (see V10/V11 migrations). If `wealthstack.bootstrap.owner-email` names the incoming
 * user's email and party 1 has no members yet, that user adopts party 1 as their personal party
 * — linking all pre-existing data to them. A no-op for everyone else and once adopted.
 *
 * Uses a programmatic [TransactionTemplate] (not `@Transactional`) so the provisioning writes are
 * atomic while the duplicate-insert recovery below runs *outside* the rolled-back transaction.
 */
open class UserProvisioningService(
    private val appUserRepository: AppUserRepository,
    private val partyRepository: PartyRepository,
    private val membershipRepository: PartyMembershipRepository,
    private val transactionTemplate: TransactionTemplate,
    private val bootstrapOwnerEmail: String,
) {

    /**
     * Race-safe (SPA startup can fire parallel first requests): a concurrent insert loses on the
     * unique `keycloak_sub` constraint and then simply re-reads the winner's row.
     */
    open fun getOrProvision(sub: String, email: String?, displayName: String?): AppUser =
        appUserRepository.findByKeycloakSub(sub) ?: try {
            transactionTemplate.execute { provision(sub, email, displayName) }!!
        } catch (_: DataIntegrityViolationException) {
            appUserRepository.findByKeycloakSub(sub)
                ?: throw IllegalStateException("Provisioning race for subject $sub left no user row")
        }

    private fun provision(sub: String, email: String?, displayName: String?): AppUser {
        val effectiveEmail = email?.takeIf { it.isNotBlank() } ?: "$sub@unknown.local"
        val effectiveName = displayName?.takeIf { it.isNotBlank() } ?: effectiveEmail
        val personalParty = bootstrapPartyToAdopt(effectiveEmail)
            ?: partyRepository.save(Party(PartyType.PERSON, effectiveName))
        val user = appUserRepository.save(AppUser(sub, effectiveEmail, effectiveName, personalParty.id!!))
        membershipRepository.save(PartyMembership(personalParty.id!!, user.id!!, PartyRole.OWNER))
        return user
    }

    private fun bootstrapPartyToAdopt(email: String): Party? {
        if (bootstrapOwnerEmail.isBlank() || !email.equals(bootstrapOwnerEmail, ignoreCase = true)) return null
        if (membershipRepository.existsByPartyId(BOOTSTRAP_PARTY_ID)) return null
        return partyRepository.findById(BOOTSTRAP_PARTY_ID).orElse(null)
    }

    companion object {
        /** Seeded by V10; owns all data that predates multi-user support. */
        const val BOOTSTRAP_PARTY_ID = 1L
    }
}
