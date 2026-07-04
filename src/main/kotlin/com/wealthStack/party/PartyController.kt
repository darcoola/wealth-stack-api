package com.wealthStack.party

import com.wealthStack.security.PartyContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * Household (ORGANIZATION party) management. Member-management endpoints operate on the party in
 * the path and require the caller to be one of its OWNERs; the [PartyContext] argument already
 * guarantees the caller is at least a member of their *active* party, but these endpoints
 * re-check against the addressed party explicitly, since it may differ from the active one.
 */
@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val partyService: PartyService,
    private val membershipRepository: PartyMembershipRepository,
) {

    @PostMapping
    fun create(ctx: PartyContext, @RequestBody request: CreatePartyRequest): PartySummary {
        val party = partyService.createOrganization(ctx.user.id!!, request.name)
        return PartySummary(party.id!!, party.name, party.type, PartyRole.OWNER)
    }

    @GetMapping("/{partyId}/members")
    fun members(ctx: PartyContext, @PathVariable partyId: Long): List<MemberDto> {
        requireMembership(ctx, partyId)
        return partyService.members(partyId)
    }

    @PostMapping("/{partyId}/members")
    fun addMember(ctx: PartyContext, @PathVariable partyId: Long, @RequestBody request: AddMemberRequest): MemberDto {
        requireOwnership(ctx, partyId)
        return partyService.addMember(partyId, request.email, request.role ?: PartyRole.MEMBER)
    }

    @PatchMapping("/{partyId}/members/{userId}")
    fun changeRole(
        ctx: PartyContext,
        @PathVariable partyId: Long,
        @PathVariable userId: Long,
        @RequestBody request: ChangeRoleRequest,
    ): MemberDto {
        requireOwnership(ctx, partyId)
        return partyService.changeRole(partyId, userId, request.role)
    }

    @DeleteMapping("/{partyId}/members/{userId}")
    fun removeMember(ctx: PartyContext, @PathVariable partyId: Long, @PathVariable userId: Long) {
        requireOwnership(ctx, partyId)
        partyService.removeMember(partyId, userId)
    }

    private fun requireMembership(ctx: PartyContext, partyId: Long): PartyMembership =
        membershipRepository.findByPartyIdAndUserId(partyId, ctx.user.id!!)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of party $partyId")

    private fun requireOwnership(ctx: PartyContext, partyId: Long) {
        if (requireMembership(ctx, partyId).role != PartyRole.OWNER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only party owners may manage members")
        }
    }
}

data class CreatePartyRequest(val name: String)

data class AddMemberRequest(val email: String, val role: PartyRole? = null)

data class ChangeRoleRequest(val role: PartyRole)
