import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { UserRole } from '@core/store/user-role.store';
import { DashboardAdministrador } from '@/app/model/dashboard-administrador';
import { DashboardAdministradorService } from '@/app/services/dashboard-administrador.service';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import {
  ATALHOS_DASHBOARD,
  formatarMoedaBRLPorGrupos,
  INDICADORES_DASHBOARD,
} from './dashboard-administrador-presentation';

type EstadoCarregamento = 'carregando' | 'erro' | 'sucesso';

@Component({
  selector: 'app-dashboard-administrador',
  standalone: true,
  imports: [
    DecimalPipe,
    EmptyStateComponent,
    MatButtonModule,
    MatIconModule,
    PageHeaderComponent,
    RouterLink,
  ],
  templateUrl: './dashboard-administrador.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardAdministradorComponent {
  private readonly dashboardService = inject(DashboardAdministradorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly userRole = inject(UserRole);

  readonly estado = signal<EstadoCarregamento>('carregando');
  readonly indicadores = signal<DashboardAdministrador | null>(null);
  readonly saudacao = computed(() => {
    const nome = this.userRole.user()?.name.trim();
    return nome ? `Olá, ${nome}` : 'Dashboard do Administrador';
  });
  readonly trilha = [
    { label: 'Painel administrativo', route: '/admin' },
    { label: 'Dashboard' },
  ] as const;
  readonly cards = INDICADORES_DASHBOARD;
  readonly atalhos = ATALHOS_DASHBOARD;
  readonly formatarMoeda = formatarMoedaBRLPorGrupos;

  constructor() {
    this.carregarIndicadores();
  }

  tentarNovamente(): void {
    this.carregarIndicadores();
  }

  private carregarIndicadores(): void {
    this.estado.set('carregando');
    this.indicadores.set(null);

    this.dashboardService.get().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (indicadores) => {
        this.indicadores.set(indicadores);
        this.estado.set('sucesso');
      },
      error: () => this.estado.set('erro'),
    });
  }
}
