package com.wealthStack.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Stateless OAuth2 resource server validating Keycloak-issued JWTs.
 *
 * Access model (note: comments avoid literal glob patterns because Kotlin block comments nest):
 *  - `api/v1/public` paths — unauthenticated (frontend bootstrap config).
 *  - `api/v1/me` — any authenticated user, including ones not yet approved; the endpoint itself
 *    reports approval state so the SPA can show a "pending approval" page.
 *  - all other `api` paths — require the `wealthstack-user` Keycloak realm role. Anyone can sign
 *    in (e.g. via Google), but they stay locked out of data endpoints until an admin assigns the
 *    role in the Keycloak console — that role assignment IS the sign-up approval.
 *  - everything else — permitted: the bundled SPA static resources and the index.html fallback
 *    (see [com.wealthStack.web.WebConfig]); the SPA handles its own login redirect.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        // No cookies/session — JWT in the Authorization header, so CSRF does not apply.
        .csrf { it.disable() }
        // Default is X-Frame-Options: DENY, which blocks keycloak-js's same-origin silent
        // check-sso iframe (silent-check-sso.html). Allow same-origin framing; cross-origin
        // clickjacking protection is preserved.
        .headers { headers -> headers.frameOptions { it.sameOrigin() } }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/me").authenticated()
                .requestMatchers("/api/**").hasRole(REQUIRED_ROLE)
                .anyRequest().permitAll()
        }
        .oauth2ResourceServer { rs -> rs.jwt { it.jwtAuthenticationConverter(keycloakRoleConverter()) } }
        .build()

    /** Maps Keycloak's `realm_access.roles` claim to `ROLE_*` authorities. */
    private fun keycloakRoleConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
            val realmAccess = jwt.claims["realm_access"] as? Map<*, *>
            val roles = realmAccess?.get("roles") as? Collection<*> ?: emptyList<Any>()
            roles.map { SimpleGrantedAuthority("ROLE_$it") as GrantedAuthority }.toMutableList()
        }
    }

    companion object {
        const val REQUIRED_ROLE = "wealthstack-user"
    }
}
