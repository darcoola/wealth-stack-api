import { Component, DOCUMENT, HostListener, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MenuModule, ButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly document = inject(DOCUMENT);

  /** Below this width the sidebar becomes an off-canvas drawer (see app.scss @media). */
  private static readonly MOBILE_BREAKPOINT = 768;

  protected readonly isMobile = signal(App.isNarrowViewport());
  // Start with the drawer closed on phones; keep the sidebar open on wider screens.
  protected readonly sidebarCollapsed = signal(App.isNarrowViewport());
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
    { label: 'Administration', icon: 'pi pi-cog', routerLink: '/administration' },
  ];

  protected toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  /** Tapping a nav item or the backdrop should dismiss the drawer on mobile (no-op on desktop). */
  protected closeSidebarOnMobile(): void {
    if (this.isMobile()) {
      this.sidebarCollapsed.set(true);
    }
  }

  @HostListener('window:resize')
  protected onResize(): void {
    const mobile = App.isNarrowViewport();
    if (mobile !== this.isMobile()) {
      this.isMobile.set(mobile);
      // Crossing the breakpoint: collapse the drawer on shrink, reveal the sidebar on grow.
      this.sidebarCollapsed.set(mobile);
    }
  }

  private static isNarrowViewport(): boolean {
    return typeof window !== 'undefined' && window.innerWidth <= App.MOBILE_BREAKPOINT;
  }

  protected toggleDarkMode(): void {
    this.darkMode.update((dark) => !dark);
    this.document.documentElement.classList.toggle('app-dark', this.darkMode());
  }
}
