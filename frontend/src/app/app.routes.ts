import { Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth.guard';
import { unsentChangesGuard } from '@core/guards/unsent-changes.guard';

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
        title: 'Acessar - Portal Lactec',
      },
      {
        path: 'recuperar-senha',
        loadComponent: () =>
          import('@/app/pages/password-recovery/password-recovery.component').then(
            (modulo) => modulo.PasswordRecoveryComponent,
          ),
        title: 'Recuperar senha - Portal Lactec',
      },
      {
        path: 'confirmar-email',
        loadComponent: () =>
          import('@/app/pages/email-confirmation/email-confirmation.component').then(
            (modulo) => modulo.EmailConfirmationComponent,
          ),
        title: 'Confirmar e-mail - Portal Lactec',
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
      { path: '', pathMatch: 'full', redirectTo: 'quotes' },
      {
        path: 'quotes',
        loadComponent: () =>
          import('@/app/pages/client/quotes/quote-list.component').then(
            (modulo) => modulo.QuoteListComponent,
          ),
        title: 'Orçamentos - Portal Lactec',
      },
      {
        path: 'quotes/:id',
        loadComponent: () =>
          import('@/app/pages/client/quotes/detail/quote-detail.component').then(
            (modulo) => modulo.QuoteDetailComponent,
          ),
        title: 'Detalhes do orçamento - Portal Lactec',
      },
      {
        path: 'quotes/:id/aceite',
        loadComponent: () =>
          import('@/app/pages/client/quotes/acceptance/acceptance-terms.component').then(
            (modulo) => modulo.AcceptanceTermsComponent,
          ),
        title: 'Termo de aceite - Portal Lactec',
        canDeactivate: [unsentChangesGuard],
      },
      {
        path: 'relatorios',
        loadComponent: () =>
          import('@/app/pages/client/reports/report-list.component').then(
            (modulo) => modulo.ReportListComponent,
          ),
        title: 'Relatórios - Portal Lactec',
      },
      // Os laudos de um orçamento só. O nome do segmento tem que ser igual ao
      // nome do input do componente: `withComponentInputBinding` casa os dois
      // pela chave, e nome diferente não é erro, é um input que nunca recebe nada.
      {
        path: 'relatorios/:quoteId',
        loadComponent: () =>
          import('@/app/pages/client/reports/quote-reports/quote-reports.component').then(
            (modulo) => modulo.QuoteReportsComponent,
          ),
        title: 'Relatórios do orçamento - Portal Lactec',
      },
      {
        path: 'equipamentos',
        loadComponent: () =>
          import('@/app/pages/client/equipment/equipment-list.component').then(
            (modulo) => modulo.EquipmentListComponent,
          ),
        title: 'Equipamentos - Portal Lactec',
      },
      {
        path: 'equipamentos/:id',
        loadComponent: () =>
          import('@/app/pages/client/equipment/detail/equipment-detail.component').then(
            (modulo) => modulo.EquipmentDetailComponent,
          ),
        title: 'Detalhes do equipamento - Portal Lactec',
      },
      {
        path: 'equipamentos/:id/indicadores',
        loadComponent: () =>
          import('@/app/pages/client/equipment/health-indicators/health-indicators.component').then(
            (modulo) => modulo.HealthIndicatorsComponent,
          ),
        title: 'Indicadores de saúde - Portal Lactec',
      },
      // Uma análise do histórico, identificada por segmento: o identificador diz
      // qual dado está na tela, então sobrevive ao recarregar e vai no link.
      //
      // O nome do segmento tem que ser igual ao nome do input do componente:
      // `withComponentInputBinding` casa os dois pela chave, e nome diferente não
      // é erro, é um input que nunca recebe nada. Endereço de rota é o caminho
      // (`/cliente/equipamentos/.../indicadores`), não o nome do parâmetro.
      {
        path: 'equipamentos/:id/indicadores/:reportId',
        loadComponent: () =>
          import('@/app/pages/client/equipment/health-indicators/health-indicators.component').then(
            (modulo) => modulo.HealthIndicatorsComponent,
          ),
        title: 'Indicadores de saúde - Portal Lactec',
      },
      {
        path: 'perfil',
        loadComponent: () =>
          import('@/app/pages/client/profile/profile.component').then(
            (modulo) => modulo.ProfileComponent,
          ),
        title: 'Meu perfil - Portal Lactec',
      },
    ],
  },

  {
    path: 'confirmar-alteracao-email',
    loadComponent: () =>
      import('@/app/pages/client/profile/confirm-email-change.component').then(
        (modulo) => modulo.ConfirmEmailChangeComponent,
      ),
    title: 'Confirmar alteração de e-mail - Portal Lactec',
  },

  {
    path: '404',
    loadComponent: () =>
      import('@/app/pages/errors/not-found/not-found.component').then((modulo) => modulo.NotFoundComponent),
    title: 'Página não encontrada - Portal Lactec',
  },
  { path: '**', redirectTo: '404' },
];
