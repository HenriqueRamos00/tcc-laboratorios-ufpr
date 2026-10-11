import { Routes } from '@angular/router';

import { authGuard, guestGuard } from '@core/guards/auth.guard';

const placeholder = (title: string, description: string, icon: string) => ({
  pageTitle: title,
  pageDescription: description,
  pageIcon: icon,
});

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
        title: 'Acessar - Portal Interno Lactec',
      },
    ],
  },

  {
    path: 'admin',
    loadComponent: () =>
      import('@/app/layouts/shell/shell.component').then((module) => module.ShellComponent),
    canActivate: [authGuard],
    data: { role: 'ADMIN' },
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('@/app/pages/placeholder/placeholder.component').then(
            (module) => module.PlaceholderComponent,
          ),
        data: placeholder(
          'Dashboard',
          'Resumo das atividades administrativas do laboratório.',
          'dashboard',
        ),
        title: 'Dashboard - Portal Interno Lactec',
      },
      ...([
        ['orcamentos', 'Orçamentos', 'Gestão dos orçamentos do laboratório.', 'description'],
      ] as const).map(([path, title, description, icon]) => ({
        path,
        loadComponent: () =>
          import('@/app/pages/placeholder/placeholder.component').then(
            (module) => module.PlaceholderComponent,
          ),
        data: placeholder(title, description, icon),
        title: title + ' - Portal Interno Lactec',
      })),
      {
        path: 'empresas',
        loadComponent: () =>
          import('@/app/pages/admin/empresas/empresas.component').then(
            (module) => module.EmpresasComponent,
          ),
        title: 'Pesquisar Empresas - Portal Interno Lactec',
      },
      {
        path: 'clientes',
        loadComponent: () =>
          import('@/app/pages/admin/clientes/clientes.component').then(
            (module) => module.ClientesComponent,
          ),
        title: 'Pesquisar Clientes - Portal Interno Lactec',
      },
      {
        path: 'tecnicos',
        loadComponent: () =>
          import('@/app/pages/admin/tecnicos/tecnicos.component').then(
            (module) => module.TecnicosComponent,
          ),
        title: 'Pesquisar Técnicos - Portal Interno Lactec',
      },
    ],
  },

  {
    path: 'tecnico',
    loadComponent: () =>
      import('@/app/layouts/shell/shell.component').then((module) => module.ShellComponent),
    canActivate: [authGuard],
    data: { role: 'TECNICO' },
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('@/app/pages/placeholder/placeholder.component').then(
            (module) => module.PlaceholderComponent,
          ),
        data: placeholder(
          'Dashboard',
          'Resumo das atividades técnicas e ensaios do laboratório.',
          'dashboard',
        ),
        title: 'Dashboard - Portal Interno Lactec',
      },
      ...([
        [
          'ensaios-amostras',
          'Pesquisar Ensaios e Amostras',
          'Pesquise ensaios e amostras disponíveis no laboratório.',
          'search',
        ],
        ['ensaios', 'Manter Ensaio', 'Cadastro e manutenção de ensaios.', 'science'],
        ['resultados', 'Resultados', 'Consulta e manutenção de resultados de ensaios.', 'fact_check'],
        ['relatorios', 'Pesquisar Relatório', 'Pesquise os relatórios técnicos.', 'description'],
      ] as const).map(([path, title, description, icon]) => ({
        path,
        loadComponent: () =>
          import('@/app/pages/placeholder/placeholder.component').then(
            (module) => module.PlaceholderComponent,
          ),
        data: placeholder(title, description, icon),
        title: title + ' - Portal Interno Lactec',
      })),
      {
        path: 'equipamentos',
        loadComponent: () =>
          import('@/app/pages/tecnico/equipamentos/equipamentos.component').then(
            (module) => module.EquipamentosComponent,
          ),
        title: 'Pesquisar Equipamentos - Portal Interno Lactec',
      },
    ],
  },

  {
    path: '404',
    loadComponent: () =>
      import('@/app/pages/errors/not-found/not-found.component').then(
        (module) => module.NotFoundComponent,
      ),
    title: 'Página não encontrada - Portal Interno Lactec',
  },
  { path: '**', redirectTo: '404' },
];
