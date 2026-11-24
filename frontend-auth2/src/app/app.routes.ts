import { Routes } from '@angular/router';

import { authGuard, guestGuard } from '@core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./layouts/wrapper-auth/wrapper-auth.component').then(
        (module) => module.WrapperAuthComponent,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/login/login.component').then((module) => module.LoginComponent),
        title: 'Entrar | Administração Auth2',
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () =>
      import('./layouts/shell/shell.component').then((module) => module.ShellComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/admin/overview.component').then((module) => module.OverviewComponent),
        title: 'Visão geral | Administração Auth2',
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
