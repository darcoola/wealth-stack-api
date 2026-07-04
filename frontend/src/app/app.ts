import { Component, DOCUMENT, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { AuthService } from './core/auth/auth.service';
import { PartyContextService } from './core/auth/party-context.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MenuModule, ButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly document = inject(DOCUMENT);
  protected readonly auth = inject(AuthService);
  protected readonly partyContext = inject(PartyContextService);

  protected readonly sidebarCollapsed = signal(false);
  protected readonly darkMode = signal(false);

  // Left-hand navigation. Add menu items here as new pages come online.
  protected readonly menuItems: MenuItem[] = [
    { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/dashboard' },
    { label: 'Operations', icon: 'pi pi-list', routerLink: '/operations' },
    { label: 'Categories', icon: 'pi pi-tags', routerLink: '/categories' },
    { label: 'Groups', icon: 'pi pi-sitemap', routerLink: '/groups' },
    { label: 'Import', icon: 'pi pi-upload', routerLink: '/import' },
    { label: 'Accounts', icon: 'pi pi-id-card', routerLink: '/accounts' },
    { label: 'Reports', icon: 'pi pi-chart-bar', routerLink: '/reports' },
    { label: 'Household', icon: 'pi pi-users', routerLink: '/household' },
    { label: 'Administration', icon: 'pi pi-cog', routerLink: '/administration' },
  ];

  /** Popup menu: switch between the user's parties, manage the household, sign out. */
  protected readonly userMenuItems = computed<MenuItem[]>(() => {
    const active = this.partyContext.activeParty();
    const partyItems: MenuItem[] = this.partyContext.parties().map((party) => ({
      label: party.name,
      icon: party.type === 'ORGANIZATION' ? 'pi pi-users' : 'pi pi-user',
      styleClass: party.id === active?.id ? 'active-party' : undefined,
      command: () => {
        if (party.id !== active?.id) {
          this.partyContext.switchTo(party);
        }
      },
    }));
    // PrimeNG's p-menu switches to grouped mode as soon as any entry has `items`; in that
    // mode top-level entries render as non-clickable submenu headers. So every top-level entry
    // must be a group — the standalone actions live under their own "Account" group.
    return [
      { label: 'Workspace', items: partyItems },
      {
        label: 'Account',
        items: [
          { label: 'Manage household', icon: 'pi pi-users', routerLink: '/household' },
          { label: 'Sign out', icon: 'pi pi-sign-out', command: () => this.auth.logout() },
        ],
      },
    ];
  });

  protected toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  protected toggleDarkMode(): void {
    this.darkMode.update((dark) => !dark);
    this.document.documentElement.classList.toggle('app-dark', this.darkMode());
  }
}
