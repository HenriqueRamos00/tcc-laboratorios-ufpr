import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { handFileToTheBrowser } from '@shared/file-download';
import {
  PageHeaderComponent,
  type Breadcrumb,
} from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';
import {
  StatusStepperComponent,
  type StatusStepperStep,
} from '@shared/components/status-stepper/status-stepper.component';

import { stageIndex, PIPELINE_STAGES } from '@/app/model/analysis-pipeline';
import type { AnalysisConclusion, PublishedReport } from '@/app/model/report';
import { ReportsService } from '@/app/services/reports.service';

// Esta tela é o fim da linha de um orçamento: mostra o trecho final do
// progresso já concluído e os laudos que aquele orçamento gerou (HU-04).
const ETAPAS: readonly StatusStepperStep[] = PIPELINE_STAGES.map((stage) => ({
  key: stage.key,
  label: stage.label,
  icon: stage.icon,
}));

@Component({
  selector: 'app-client-relatorios-do-quote',
  standalone: true,
  imports: [
    MatIconModule,
    PageHeaderComponent,
    SearchFieldComponent,
    EmptyStateComponent,
    StatusStepperComponent,
  ],
  templateUrl: './quote-reports.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteReportsComponent {
  /** Vem do parâmetro de rota, via withComponentInputBinding. */
  readonly quoteId = input.required<string>();

  private readonly reports = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly stages = ETAPAS;
  protected readonly etapaAtual = stageIndex('relatorio-publicado');

  protected readonly searchTerm = signal('');
  protected readonly conclusion = signal<AnalysisConclusion | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly downloadFailed = signal(false);

  protected readonly breadcrumbs = computed<readonly Breadcrumb[]>(() => [
    { label: 'Relatórios', route: '/cliente/relatorios' },
    { label: this.conclusion()?.quoteCode ?? 'Orçamento' },
  ]);

  protected readonly visibleReports = computed(() => {
    const list = this.conclusion()?.reports ?? [];
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return list;
    return list.filter(
      (report) =>
        report.code.toLowerCase().includes(term) || report.protocol.toLowerCase().includes(term),
    );
  });

  constructor() {
    // O router reaproveita a instância quando só o identificador muda, e
    // ngOnInit não roda de novo.
    toObservable(this.quoteId)
      .pipe(
        tap(() => {
          this.conclusion.set(null);
          this.failed.set(false);
          this.loading.set(true);
          this.searchTerm.set('');
          this.downloadFailed.set(false);
        }),
        switchMap((id) =>
          this.reports.analysisConclusion(id).pipe(
            catchError(() => {
              this.failed.set(true);
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((conclusion) => {
        this.conclusion.set(conclusion);
        this.loading.set(false);
      });
  }

  protected downloadReport(report: PublishedReport): void {
    this.downloadFailed.set(false);
    this.reports
      .downloadReport(report.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (file) => handFileToTheBrowser(file, `relatorio-${report.id}.pdf`),
        error: () => this.downloadFailed.set(true),
      });
  }
}
