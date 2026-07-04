package com.wealthStack.party

import org.springframework.data.jpa.repository.JpaRepository

interface PartyRepository : JpaRepository<Party, Long>

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByKeycloakSub(keycloakSub: String): AppUser?

    fun findByEmailIgnoreCase(email: String): AppUser?
}

interface PartyMembershipRepository : JpaRepository<PartyMembership, Long> {
    fun findAllByUserId(userId: Long): List<PartyMembership>

    fun findAllByPartyId(partyId: Long): List<PartyMembership>

    fun findByPartyIdAndUserId(partyId: Long, userId: Long): PartyMembership?

    fun existsByPartyId(partyId: Long): Boolean
}
