package com.wealthStack.security

import com.wealthStack.party.AppUser
import com.wealthStack.party.PartyMembershipRepository
import com.wealthStack.party.PartyRole
import com.wealthStack.party.UserProvisioningService
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException

/**
 * The tenancy context of one request: who is calling and which party's data they are acting on.
 * Controllers declare a [PartyContext] parameter and pass [partyId] into every service/finder
 * call — that explicit threading (rather than an implicit session filter) is the app's whole
 * multi-tenancy mechanism, so nothing below the controller layer touches request state.
 */
data class PartyContext(
    val user: AppUser,
    val partyId: Long,
    val role: PartyRole,
)

/**
 * Resolves [PartyContext] controller parameters and is the single tenancy gate:
 *  1. provisions the user on first sight (JIT, from JWT claims),
 *  2. picks the active party from the `X-Party-Id` header (absent → the user's personal party),
 *  3. rejects with 403 unless the user is actually a member of that party — which is what makes
 *     a spoofed `X-Party-Id` harmless.
 */
class PartyContextArgumentResolver(
    private val provisioningService: UserProvisioningService,
    private val membershipRepository: PartyMembershipRepository,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == PartyContext::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): PartyContext {
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No bearer token")
        val jwt = authentication.token
        val user = provisioningService.getOrProvision(
            jwt.subject,
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
        )
        val requestedPartyId = webRequest.getHeader(PARTY_HEADER)?.let {
            it.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $PARTY_HEADER")
        }
        val partyId = requestedPartyId ?: user.personalPartyId
        val membership = membershipRepository.findByPartyIdAndUserId(partyId, user.id!!)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of party $partyId")
        return PartyContext(user, partyId, membership.role)
    }

    companion object {
        const val PARTY_HEADER = "X-Party-Id"
    }
}
