import { Injectable, signal } from '@angular/core';
import { MeResponse, PartySummary } from '../me';

const STORAGE_KEY = 'wealthstack.activePartyId';

/**
 * Which party the user is currently acting as. The auth interceptor stamps it on every API call
 * as the X-Party-Id header; the backend validates membership per request, so a stale or tampered
 * value can only produce a 403, never someone else's data.
 */
@Injectable({ providedIn: 'root' })
export class PartyContextService {
  readonly parties = signal<PartySummary[]>([]);
  readonly activeParty = signal<PartySummary | null>(null);

  /** Restores the persisted choice, falling back to the personal party when it is no longer valid. */
  initFrom(me: MeResponse): void {
    this.parties.set(me.parties);
    const stored = Number(localStorage.getItem(STORAGE_KEY));
    const active =
      me.parties.find((p) => p.id === stored) ??
      me.parties.find((p) => p.id === me.personalPartyId) ??
      me.parties[0] ??
      null;
    this.activeParty.set(active);
  }

  activePartyId(): number | null {
    return this.activeParty()?.id ?? null;
  }

  /** Persists the switch and reloads so every page refetches under the new party. */
  switchTo(party: PartySummary): void {
    localStorage.setItem(STORAGE_KEY, String(party.id));
    location.reload();
  }
}
