import { Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Shown to users who signed in (e.g. with Google) but have not yet been granted the
 * `wealthstack-user` role in Keycloak — sign-up is approval-gated.
 */
@Component({
  selector: 'app-pending-approval',
  imports: [ButtonModule],
  template: `
    <div class="pending-wrapper">
      <i class="pi pi-hourglass pending-icon"></i>
      <h2>Almost there</h2>
      <p>
        Your account was created, but it needs to be approved before you can use WealthStack.
        Ask the administrator to activate it, then sign in again.
      </p>
      <p-button label="Sign out" icon="pi pi-sign-out" (onClick)="auth.logout()" />
    </div>
  `,
  styles: `
    .pending-wrapper {
      max-width: 28rem;
      margin: 15vh auto 0;
      text-align: center;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      align-items: center;
    }
    .pending-icon {
      font-size: 3rem;
      color: var(--p-primary-color);
    }
  `,
})
export class PendingApproval {
  protected readonly auth = inject(AuthService);
}
