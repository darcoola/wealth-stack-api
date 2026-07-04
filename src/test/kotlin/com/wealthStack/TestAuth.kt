package com.wealthStack

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.client.RestTemplate
import java.time.Instant

/**
 * Test stand-in for Keycloak. HTTP tests hit a live random port with [RestTemplate], so MockMvc
 * `jwt()` post-processors don't apply; instead this stub [JwtDecoder] accepts any bearer token and
 * derives the identity from the token string itself:
 *
 *  - `"alice"`              → sub `alice`, email `alice@test.local`, approved (`wealthstack-user` role)
 *  - `"alice~unapproved"`   → same identity but without the role (signed in, not yet approved)
 *
 * `~` is the flag separator because it is one of the few characters the RFC 6750 bearer-token
 * syntax allows (Spring rejects tokens with characters like `|` before they reach the decoder).
 *
 * Import with `@Import(TestAuth::class)` and call endpoints via [rest] (interceptor adds the
 * bearer header to every request) or attach [authHeaders] manually.
 */
@TestConfiguration
class TestAuth {

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder { token ->
        val parts = token.split("~")
        val sub = parts[0]
        val roles = if ("unapproved" in parts) emptyList() else listOf("wealthstack-user")
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject(sub)
            .claim("email", "$sub@test.local")
            .claim("name", "Test $sub")
            .claim("realm_access", mapOf("roles" to roles))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    companion object {
        const val DEFAULT_SUB = "test-user"

        fun authHeaders(token: String = DEFAULT_SUB): HttpHeaders =
            HttpHeaders().apply { setBearerAuth(token) }

        /** A [RestTemplate] that sends `Authorization: Bearer <token>` on every request. */
        fun rest(token: String = DEFAULT_SUB): RestTemplate = RestTemplate().apply {
            interceptors.add({ request, body, execution ->
                request.headers.setBearerAuth(token)
                execution.execute(request, body)
            })
        }
    }
}
