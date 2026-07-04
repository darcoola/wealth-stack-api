package com.wealthStack.security

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Unauthenticated bootstrap endpoint: the SPA fetches the Keycloak coordinates from here before
 * initializing OIDC, so the frontend needs no per-environment build (`wealthstack.auth.*` in
 * application.yml is the single source of truth, overridable via env vars in prod).
 */
@RestController
@RequestMapping("/api/v1/public/auth-config")
class AuthConfigController(
    private val url: String,
    private val realm: String,
    private val clientId: String,
) {

    @GetMapping
    fun authConfig(): Map<String, String> = mapOf(
        "url" to url,
        "realm" to realm,
        "clientId" to clientId,
    )
}
