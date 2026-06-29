import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard · WealthStack',
    loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'operations',
    title: 'Operations · WealthStack',
    loadComponent: () => import('./pages/operations/operations').then((m) => m.Operations),
  },
  {
    path: 'import',
    title: 'Import · WealthStack',
    loadComponent: () => import('./pages/import/import').then((m) => m.Import),
  },
  {
    path: 'accounts',
    title: 'Accounts · WealthStack',
    loadComponent: () => import('./pages/accounts/accounts').then((m) => m.Accounts),
  },
  {
    path: 'reports',
    title: 'Reports · WealthStack',
    loadComponent: () => import('./pages/reports/reports').then((m) => m.Reports),
  },
  { path: '**', redirectTo: 'dashboard' },
];
