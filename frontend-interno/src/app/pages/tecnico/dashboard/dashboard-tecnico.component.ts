import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { UserRole } from '@core/store/user-role.store';
import { DashboardTecnico } from '@/app/model/dashboard-tecnico';
import { validationStatusLabel } from '@/app/model/relatorio-presentation';
import { DashboardTecnicoService } from '@/app/services/dashboard-tecnico.service';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { BarChartComponent } from '@shared/components/bar-chart/bar-chart.component';
import {
  ATALHOS_DASHBOARD_TECNICO,
  classeStatusLaudoDashboard,
  INDICADORES_DASHBOARD_TECNICO,
  resumoLaudosPendentes,
  linhasAtividadeLaudos,
  linhasIdadeLaudos,
  SERIES_ATIVIDADE_LAUDOS,
  SERIES_IDADE_LAUDOS,
} from './dashboard-tecnico-presentation';

type EstadoCarregamento = 'carregando' | 'erro' | 'sucesso';

@Component({
  selector: 'app-dashboard-tecnico',
  standalone: true,
  imports: [
    DecimalPipe,
    DatePipe,
    BarChartComponent,
    EmptyStateComponent,
    MatButtonModule,
    MatIconModule,
    PageHeaderComponent,
    RouterLink,
  ],
  templateUrl: './dashboard-tecnico.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardTecnicoComponent {
  private readonly dashboardService = inject(DashboardTecnicoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly userRole = inject(UserRole);

  readonly estado = signal<EstadoCarregamento>('carregando');
  readonly dados = signal<DashboardTecnico | null>(null);
  readonly saudacao = computed(() => {
    const nome = this.userRole.user()?.name.trim();
    return nome ? `Olá, ${nome}` : 'Dashboard de técnico';
  });
  readonly trilha = [
    { label: 'Painel técnico', route: '/tecnico' },
    { label: 'Dashboard' },
  ] as const;
  readonly indicadores = INDICADORES_DASHBOARD_TECNICO;
  readonly atalhos = ATALHOS_DASHBOARD_TECNICO;
  readonly rotuloStatus = validationStatusLabel;
  readonly classeStatus = classeStatusLaudoDashboard;
  readonly seriesAtividade = SERIES_ATIVIDADE_LAUDOS;
  readonly seriesIdade = SERIES_IDADE_LAUDOS;
  readonly atividadeLaudos = computed(() => {
    const analytics = this.dados()?.reportAnalytics;
    return analytics ? linhasAtividadeLaudos(analytics.monthlyReports) : [];
  });
  readonly idadeLaudos = computed(() => {
    const analytics = this.dados()?.reportAnalytics;
    return analytics ? linhasIdadeLaudos(analytics.pendingReportAge) : [];
  });
  readonly resumoPendencias = computed(() => {
    const dados = this.dados();
    return dados
      ? resumoLaudosPendentes(dados.pendingReportsTotal, dados.pendingReports.length)
      : '';
  });

  constructor() {
    this.carregar();
  }

  tentarNovamente(): void {
    this.carregar();
  }

  private carregar(): void {
    this.estado.set('carregando');
    this.dados.set(null);

    this.dashboardService.get().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (dados) => {
        this.dados.set(dados);
        this.estado.set('sucesso');
      },
      error: () => this.estado.set('erro'),
    });
  }
}
