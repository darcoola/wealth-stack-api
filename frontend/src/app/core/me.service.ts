import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MeResponse } from './me';
import { PartyContextService } from './auth/party-context.service';

/** Loads the signed-in user's profile + parties once and seeds the active-party context. */
@Injectable({ providedIn: 'root' })
export class MeService {
  private readonly http = inject(HttpClient);
  private readonly partyContext = inject(PartyContextService);
  private me: MeResponse | null = null;

  async load(): Promise<MeResponse> {
    if (this.me) {
      return this.me;
    }
    const me = await firstValueFrom(this.http.get<MeResponse>('/api/v1/me'));
    this.me = me;
    if (me.approved) {
      this.partyContext.initFrom(me);
    }
    return me;
  }

  /** Forces the next [load] to refetch (e.g. after creating a household). */
  invalidate(): void {
    this.me = null;
  }
}
