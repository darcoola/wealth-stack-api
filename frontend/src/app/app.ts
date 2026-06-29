import { Component, DOCUMENT, inject, signal } from '@angular/core';
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

  protected readonly sidebarCollapsed = signal(false);
  protected readonly darkMode = signal(false);

  // Left-hand navigation. Add menu items here as new pages come online.
  protected readonly menuItems: MenuItem[] = [
    { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/dashboard' },
    { label: 'Operations', icon: 'pi pi-list', routerLink: '/operations' },
    { label: 'Import', icon: 'pi pi-upload', routerLink: '/import' },
    { label: 'Accounts', icon: 'pi pi-id-card', routerLink: '/accounts' },
    { label: 'Reports', icon: 'pi pi-chart-bar', routerLink: '/reports' },
  ];

  protected toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  protected toggleDarkMode(): void {
    this.darkMode.update((dark) => !dark);
    this.document.documentElement.classList.toggle('app-dark', this.darkMode());
  }
}
