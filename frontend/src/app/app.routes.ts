import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    // Outside the guard: reachable by signed-in-but-unapproved users.
    path: 'pending-approval',
    title: 'Pending approval · WealthStack',
    loadComponent: () => import('./pages/pending-approval/pending-approval').then((m) => m.PendingApproval),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
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
        path: 'categories',
        title: 'Categories · WealthStack',
        loadComponent: () => import('./pages/categories/categories').then((m) => m.Categories),
      },
      {
        path: 'groups',
        title: 'Groups · WealthStack',
        loadComponent: () => import('./pages/category-groups/category-groups').then((m) => m.CategoryGroups),
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
      {
        path: 'household',
        title: 'Household · WealthStack',
        loadComponent: () => import('./pages/household/household').then((m) => m.Household),
      },
      {
        path: 'administration',
        title: 'Administration · WealthStack',
        loadComponent: () => import('./pages/administration/administration').then((m) => m.Administration),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
