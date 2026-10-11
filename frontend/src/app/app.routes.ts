import { Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth.guard';

import { CLIENT_ROLE } from '@/app/model/roles';
import { DEMO_DATA_ENABLED } from '@/app/environment/env';

const funcionalidadeIndisponivel = () =>
  import('@/app/shared/components/unavailable-feature/unavailable-feature.component').then(
    (modulo) => modulo.UnavailableFeatureComponent,
  );

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
        title: 'Acessar - Portal Lactec',
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
        title: 'Orçamentos - Portal Lactec',
      },
      {
        path: 'orcamentos/:id',
        loadComponent: () =>
          import('@/app/pages/cliente/orcamentos/detalhe/orcamento-detalhe.component').then(
            (modulo) => modulo.OrcamentoDetalheComponent,
          ),
        title: 'Detalhes do orçamento - Portal Lactec',
      },
      {
        path: 'orcamentos/:id/aceite',
        loadComponent: () =>
          import('@/app/shared/components/unavailable-feature/unavailable-feature.component').then(
            (modulo) => modulo.UnavailableFeatureComponent,
          ),
        data: {
          titulo: 'Aceite do orçamento',
          descricao: 'O aceite e a recusa ainda não estão disponíveis.',
        },
        title: 'Termo de aceite - Portal Lactec',
      },
      {
        path: 'relatorios',
        loadComponent: DEMO_DATA_ENABLED
          ? () => import('@/app/pages/cliente/relatorios/relatorios.component').then(
              (modulo) => modulo.ClientRelatoriosComponent,
            )
          : funcionalidadeIndisponivel,
        data: {
          titulo: 'Relatórios',
          descricao: 'Os relatórios e resultados ainda não estão disponíveis.',
        },
        title: 'Relatórios - Portal Lactec',
      },
      {
        path: 'equipamentos',
        loadComponent: DEMO_DATA_ENABLED
          ? () => import('@/app/pages/cliente/equipamentos/equipamentos.component').then(
              (modulo) => modulo.ClientEquipamentosComponent,
            )
          : funcionalidadeIndisponivel,
        data: {
          titulo: 'Equipamentos',
          descricao: 'Esta funcionalidade ainda não está disponível.',
        },
        title: 'Equipamentos - Portal Lactec',
      },
      {
        path: 'equipamentos/:id',
        loadComponent: DEMO_DATA_ENABLED
          ? () => import('@/app/pages/cliente/equipamentos/detalhe/equipamento-detalhe.component').then(
              (modulo) => modulo.EquipamentoDetalheComponent,
            )
          : funcionalidadeIndisponivel,
        data: {
          titulo: 'Detalhes do equipamento',
          descricao: 'Esta funcionalidade ainda não está disponível.',
        },
        title: 'Detalhes do equipamento - Portal Lactec',
      },
      {
        path: 'equipamentos/:id/indicadores',
        loadComponent: DEMO_DATA_ENABLED
          ? () => import('@/app/pages/cliente/equipamentos/indicadores/indicadores.component').then(
              (modulo) => modulo.IndicadoresComponent,
            )
          : funcionalidadeIndisponivel,
        data: {
          titulo: 'Indicadores de saúde',
          descricao: 'Esta funcionalidade ainda não está disponível.',
        },
        title: 'Indicadores de saúde - Portal Lactec',
      },
      {
        path: 'equipamentos/:id/indicadores/:relatorioId',
        loadComponent: DEMO_DATA_ENABLED
          ? () => import('@/app/pages/cliente/equipamentos/indicadores/indicadores.component').then(
              (modulo) => modulo.IndicadoresComponent,
            )
          : funcionalidadeIndisponivel,
        data: {
          titulo: 'Indicadores de saúde',
          descricao: 'Esta funcionalidade ainda não está disponível.',
        },
        title: 'Indicadores da análise - Portal Lactec',
      },
    ],
  },

  {
    path: '404',
    loadComponent: () =>
      import('@/app/pages/errors/not-found/not-found.component').then((modulo) => modulo.NotFoundComponent),
    title: 'Página não encontrada - Portal Lactec',
  },
  { path: '**', redirectTo: '404' },
];
