import { Routes } from '@angular/router';

import { adminAuthGuard, guestGuard, setupGuard } from '@core/guards/admin-auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },

  {
    path: '',
    loadComponent: () =>
      import('@/app/layouts/wrapper-auth/wrapper-auth.component').then(
        (module) => module.WrapperAuthComponent,
      ),
    children: [
      {
        path: 'login',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('@/app/pages/login/login.component').then((module) => module.LoginComponent),
        title: 'Acessar - Administração de Acesso Lactec',
      },
      {
        path: 'setup',
        canActivate: [setupGuard],
        loadComponent: () =>
          import('@/app/pages/setup/setup.component').then((module) => module.SetupComponent),
        title: 'Configuração inicial - Administração de Acesso Lactec',
      },
    ],
  },

  {
    path: 'admin',
    loadComponent: () =>
      import('@/app/layouts/shell/shell.component').then((module) => module.ShellComponent),
    canActivate: [adminAuthGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'users' },
      {
        path: 'users',
        loadComponent: () =>
          import('@/app/pages/admin/users/users.component').then((module) => module.UsersComponent),
        title: 'Usuários - Administração de Acesso Lactec',
      },
      {
        path: 'companies',
        loadComponent: () =>
          import('@/app/pages/admin/companies/companies.component').then(
            (module) => module.CompaniesComponent,
          ),
        title: 'Empresas - Administração de Acesso Lactec',
      },
      {
        path: 'api-keys',
        loadComponent: () =>
          import('@/app/pages/admin/api-keys/api-keys.component').then(
            (module) => module.ApiKeysComponent,
          ),
        title: 'Integrações - Administração de Acesso Lactec',
      },
    ],
  },

  {
    path: '404',
    loadComponent: () =>
      import('@/app/pages/errors/not-found/not-found.component').then(
        (module) => module.NotFoundComponent,
      ),
    title: 'Página não encontrada - Administração de Acesso Lactec',
  },
  { path: '**', redirectTo: '404' },
];
