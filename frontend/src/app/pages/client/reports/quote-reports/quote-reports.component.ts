import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, EMPTY, switchMap, tap } from 'rxjs';

import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import {
  PageHeaderComponent,
  type Breadcrumb,
} from '@shared/components/page-header/page-header.component';
import { PaginationComponent } from '@shared/components/pagination/pagination.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';
import {
  StatusStepperComponent,
  type StatusStepperStep,
} from '@shared/components/status-stepper/status-stepper.component';

import { isClosed, PIPELINE_STAGES, stageIndex } from '@/app/model/analysis-pipeline';
import { DEFAULT_PAGE_SIZE } from '@/app/model/page';
import type { Quote } from '@/app/model/quote';
import { recognizeStage } from '@/app/model/quote-presentation';
import type { Report } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { ReportTableComponent } from '../report-table/report-table.component';

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
    DataUnavailableComponent,
    PaginationComponent,
    ReportTableComponent,
  ],
  templateUrl: './quote-reports.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteReportsComponent {
  /** Vem do parâmetro de rota, via withComponentInputBinding. */
  readonly quoteId = input.required<string>();

  private readonly quotes = inject(QuotesService);
  private readonly reports = inject(ReportsService);

  protected readonly stages = ETAPAS;
  protected readonly pageSize = DEFAULT_PAGE_SIZE;

  protected readonly searchTerm = signal('');
  protected readonly quote = signal<Quote | null>(null);
  protected readonly reportList = signal<readonly Report[] | null>(null);
  protected readonly page = signal(1);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly reportsLoading = signal(false);
  protected readonly testType = signal<string | null>(null);
  protected readonly summary = signal<string | null>(null);
  protected readonly failed = signal(false);

  protected readonly stage = computed(() => {
    const quote = this.quote();
    return quote ? recognizeStage(quote.stage ?? '') : null;
  });

  protected readonly currentStageIndex = computed(() => {
    const stage = this.stage();
    return stage && !isClosed(stage) ? stageIndex(stage) : null;
  });

  protected readonly breadcrumbs = computed<readonly Breadcrumb[]>(() => [
    { label: 'Relatórios', route: '/cliente/relatorios' },
    { label: this.quote()?.code ?? 'Orçamento' },
  ]);

  protected readonly visibleReports = computed(() => {
    const list = this.reportList() ?? [];
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return list;
    return list.filter((report) =>
      [report.identification, report.requestNumber].some((value) =>
        (value ?? '').toLowerCase().includes(term),
      ),
    );
  });

  constructor() {
    const quote = toObservable(this.quoteId).pipe(
      tap(() => {
        this.quote.set(null);
        this.testType.set(null);
        this.summary.set(null);
        this.reportList.set(null);
        this.failed.set(false);
        this.loading.set(true);
        this.searchTerm.set('');
        this.page.set(1);
      }),
      switchMap((id) =>
        this.quotes.getQuoteById(id).pipe(
          catchError(() => {
            this.failed.set(true);
            this.loading.set(false);
            return EMPTY;
          }),
        ),
      ),
      tap((loaded) => this.quote.set(loaded)),
    );

    combineLatest([quote, toObservable(this.page)])
      .pipe(
        tap(() => this.reportsLoading.set(true)),
        switchMap(([loaded, page]) =>
          this.reports.list({ quoteId: loaded.id, quoteCode: loaded.code, page, pageSize: this.pageSize }).pipe(
            catchError(() => {
              this.failed.set(true);
              this.loading.set(false);
              this.reportsLoading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.reportList.set(result.items);
        this.testType.set(result.testType ?? null);
        this.summary.set(result.summary ?? null);
        this.total.set(result.total);
        this.loading.set(false);
        this.reportsLoading.set(false);
      });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
  }
}
