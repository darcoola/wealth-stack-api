package com.wealthStack.party

import org.springframework.transaction.annotation.Transactional

/**
 * Managing shared (ORGANIZATION) parties — households. Deliberately minimal for friends/family
 * scale: create a household, add an existing user by email, change a member's role, remove a
 * member. No invitations, renames, or deletes; a user someone wants to add must simply have
 * signed in once before.
 */
open class PartyService(
    private val partyRepository: PartyRepository,
    private val appUserRepository: AppUserRepository,
    private val membershipRepository: PartyMembershipRepository,
) {

    @Transactional
    open fun createOrganization(creatorUserId: Long, name: String): Party {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Party name must not be blank" }
        val party = partyRepository.save(Party(PartyType.ORGANIZATION, trimmed))
        membershipRepository.save(PartyMembership(party.id!!, creatorUserId, PartyRole.OWNER))
        return party
    }

    open fun members(partyId: Long): List<MemberDto> {
        val memberships = membershipRepository.findAllByPartyId(partyId)
        val usersById = appUserRepository.findAllById(memberships.map { it.userId }).associateBy { it.id }
        return memberships.mapNotNull { membership ->
            usersById[membership.userId]?.let {
                MemberDto(it.id!!, it.email, it.displayName, membership.role)
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    /** Adds an existing user (they must have signed in at least once) to the party. */
    @Transactional
    open fun addMember(partyId: Long, email: String, role: PartyRole): MemberDto {
        val user = appUserRepository.findByEmailIgnoreCase(email.trim())
            ?: throw IllegalArgumentException(
                "No user with email '${email.trim()}' — ask them to sign in once first."
            )
        require(membershipRepository.findByPartyIdAndUserId(partyId, user.id!!) == null) {
            "${user.displayName} is already a member"
        }
        membershipRepository.save(PartyMembership(partyId, user.id!!, role))
        return MemberDto(user.id!!, user.email, user.displayName, role)
    }

    @Transactional
    open fun changeRole(partyId: Long, userId: Long, role: PartyRole): MemberDto {
        val membership = membershipRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw IllegalArgumentException("User $userId is not a member of party $partyId")
        if (membership.role == PartyRole.OWNER && role != PartyRole.OWNER) {
            requireNotLastOwner(partyId)
        }
        membership.role = role
        membershipRepository.save(membership)
        val user = appUserRepository.findById(userId).orElseThrow()
        return MemberDto(user.id!!, user.email, user.displayName, role)
    }

    @Transactional
    open fun removeMember(partyId: Long, userId: Long) {
        val membership = membershipRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw IllegalArgumentException("User $userId is not a member of party $partyId")
        if (membership.role == PartyRole.OWNER) {
            requireNotLastOwner(partyId)
        }
        membershipRepository.delete(membership)
    }

    private fun requireNotLastOwner(partyId: Long) {
        val owners = membershipRepository.findAllByPartyId(partyId).count { it.role == PartyRole.OWNER }
        require(owners > 1) { "A party must keep at least one owner" }
    }
}

data class MemberDto(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: PartyRole,
)
