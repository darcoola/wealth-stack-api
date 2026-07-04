import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';

/**
 * Thin wrapper around keycloak-js. Initialized once at app startup (see app.config.ts): fetches
 * the Keycloak coordinates from the backend's public auth-config endpoint (no per-environment
 * frontend build needed), then runs a silent `check-sso`. Unauthenticated users are not redirected
 * here — the route guard triggers the login redirect so deep links survive the round trip.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private keycloak: Keycloak | null = null;

  async init(): Promise<void> {
    // Plain fetch: runs before the app is stable and must not pass through the auth interceptor.
    const response = await fetch('/api/v1/public/auth-config');
    if (!response.ok) {
      throw new Error(`Failed to load auth config: HTTP ${response.status}`);
    }
    const config: { url: string; realm: string; clientId: string } = await response.json();

    this.keycloak = new Keycloak(config);
    await this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
    });
  }

  isAuthenticated(): boolean {
    return this.keycloak?.authenticated === true;
  }

  /** Redirects to the Keycloak login page (which also offers Google). */
  login(redirectUri: string = location.href): void {
    this.keycloak?.login({ redirectUri });
  }

  logout(): void {
    // Trailing slash so the URI matches the realm's `http://.../*` post-logout patterns
    // (Keycloak's wildcard match rejects the bare origin without a path).
    this.keycloak?.logout({ redirectUri: `${location.origin}/` });
  }

  get displayName(): string {
    const token = this.keycloak?.tokenParsed as { name?: string; email?: string } | undefined;
    return token?.name || token?.email || '';
  }

  /** Current access token, refreshed when it expires within 30s. Null when not signed in. */
  async token(): Promise<string | null> {
    if (!this.keycloak?.authenticated) {
      return null;
    }
    try {
      await this.keycloak.updateToken(30);
    } catch {
      // Refresh token expired: force a fresh interactive login.
      this.keycloak.login();
      return null;
    }
    return this.keycloak.token ?? null;
  }
}
