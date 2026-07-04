import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { PartyContextService } from './party-context.service';

/**
 * Attaches the bearer token (refreshing it when needed) and the active party header to every API
 * request. Public endpoints are passed through untouched.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.url.startsWith('/api/v1/public/')) {
    return next(req);
  }
  const auth = inject(AuthService);
  const partyContext = inject(PartyContextService);

  return from(auth.token()).pipe(
    switchMap((token) => {
      let headers = req.headers;
      if (token) {
        headers = headers.set('Authorization', `Bearer ${token}`);
      }
      const partyId = partyContext.activePartyId();
      if (partyId !== null) {
        headers = headers.set('X-Party-Id', String(partyId));
      }
      return next(req.clone({ headers }));
    })
  );
};
