import { Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth.guard';

import { CLIENT_ROLE } from '@/app/model/roles';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },

  {
    path: '',
    loadComponent: () =>
      import('@/app/layouts/wrapper-auth/wrapper-auth.component').then(
        (modulo) => modulo.WrapperAuthComponent,
      ),
    children: [
      {
        path: 'login',
        loadComponent: () =>
          import('@/app/pages/login/login.component').then((modulo) => modulo.LoginComponent),
        title: 'Acessar — Portal Lactec',
      },
    ],
  },

  {
    path: 'cliente',
    loadComponent: () =>
      import('@/app/layouts/shell/shell.component').then((modulo) => modulo.ShellComponent),
    canActivate: [authGuard],
    data: { role: CLIENT_ROLE },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'orcamentos' },
      {
        path: 'orcamentos',
        loadComponent: () =>
          import('@/app/pages/cliente/orcamentos/orcamentos.component').then(
            (modulo) => modulo.ClientOrcamentosComponent,
          ),
        title: 'Orçamentos — Portal Lactec',
      },
      {
        path: 'orcamentos/:id',
        loadComponent: () =>
          import('@/app/pages/cliente/orcamentos/detalhe/orcamento-detalhe.component').then(
            (modulo) => modulo.OrcamentoDetalheComponent,
          ),
        title: 'Detalhes do orçamento — Portal Lactec',
      },
      {
        path: 'orcamentos/:id/aceite',
        loadComponent: () =>
          import('@/app/pages/cliente/orcamentos/aceite/termo-de-aceite.component').then(
            (modulo) => modulo.TermoDeAceiteComponent,
          ),
        title: 'Termo de aceite — Portal Lactec',
      },
      {
        path: 'relatorios',
        loadComponent: () =>
          import('@/app/pages/cliente/relatorios/relatorios.component').then(
            (modulo) => modulo.ClientRelatoriosComponent,
          ),
        title: 'Relatórios — Portal Lactec',
      },
      {
        path: 'equipamentos',
        loadComponent: () =>
          import('@/app/pages/cliente/equipamentos/equipamentos.component').then(
            (modulo) => modulo.ClientEquipamentosComponent,
          ),
        title: 'Equipamentos — Portal Lactec',
      },
      {
        path: 'equipamentos/:id',
        loadComponent: () =>
          import('@/app/pages/cliente/equipamentos/detalhe/equipamento-detalhe.component').then(
            (modulo) => modulo.EquipamentoDetalheComponent,
          ),
        title: 'Detalhes do equipamento — Portal Lactec',
      },
      {
        path: 'equipamentos/:id/indicadores',
        loadComponent: () =>
          import('@/app/pages/cliente/equipamentos/indicadores/indicadores.component').then(
            (modulo) => modulo.IndicadoresComponent,
          ),
        title: 'Indicadores de saúde — Portal Lactec',
      },
    ],
  },

  {
    path: '404',
    loadComponent: () =>
      import('@/app/pages/errors/not-found/not-found.component').then((modulo) => modulo.NotFoundComponent),
    title: 'Página não encontrada — Portal Lactec',
  },
  { path: '**', redirectTo: '404' },
];
