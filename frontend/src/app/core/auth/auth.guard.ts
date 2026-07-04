import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { MeService } from '../me.service';

/**
 * Gate for all app pages: unauthenticated users are redirected to Keycloak (preserving the deep
 * link), authenticated-but-unapproved users land on the pending-approval page.
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const meService = inject(MeService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    auth.login(location.origin + state.url);
    return false;
  }

  const me = await meService.load();
  if (!me.approved) {
    return router.parseUrl('/pending-approval');
  }
  return true;
};
