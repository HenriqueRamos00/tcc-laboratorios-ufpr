import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';
import {
  StatusStepperComponent,
  type StatusStepperStep,
} from '@shared/components/status-stepper/status-stepper.component';

import { indiceDaEtapa, LINHA_DE_ETAPAS } from '@/app/model/analysis-pipeline';
import type { ConclusaoDaAnalise } from '@/app/model/report';
import { ReportsService } from '@/app/services/reports.service';

// A tela de relatórios é o fim da linha: mostra o trecho final do progresso já
// concluído e a lista de laudos publicados (HU-02 critério 6 e HU-04).
const ETAPAS: readonly StatusStepperStep[] = LINHA_DE_ETAPAS.map((etapa) => ({
  key: etapa.chave,
  label: etapa.rotulo,
  icon: etapa.icone,
}));

@Component({
  selector: 'app-client-relatorios',
  standalone: true,
  imports: [
    MatIconModule,
    PageHeaderComponent,
    SearchFieldComponent,
    EmptyStateComponent,
    StatusStepperComponent,
  ],
  templateUrl: './relatorios.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientRelatoriosComponent {
  private readonly reports = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly etapas = ETAPAS;
  protected readonly etapaAtual = indiceDaEtapa('relatorio-publicado');

  protected readonly termoDeBusca = signal('');
  protected readonly conclusao = signal<ConclusaoDaAnalise | null>(null);
  protected readonly falhou = signal(false);

  protected readonly visiveis = computed(() => {
    const lista = this.conclusao()?.relatorios ?? [];
    const termo = this.termoDeBusca().trim().toLowerCase();
    if (!termo) return lista;
    return lista.filter(
      (relatorio) =>
        relatorio.codigo.toLowerCase().includes(termo) ||
        relatorio.protocolo.toLowerCase().includes(termo),
    );
  });

  constructor() {
    this.reports
      .conclusaoDaAnalise()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (dados) => this.conclusao.set(dados),
        error: () => this.falhou.set(true),
      });
  }
}
