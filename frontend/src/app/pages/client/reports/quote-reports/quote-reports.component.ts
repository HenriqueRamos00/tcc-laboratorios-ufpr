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
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  EMPTY,
  filter,
  switchMap,
  tap,
} from 'rxjs';

import {
  isMeasured,
  sameQuery,
  unmeasuredQuery,
} from '@shared/components/data-table/data-table.model';

import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import {
  PageHeaderComponent,
  type Breadcrumb,
} from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';
import { LoadingRegionComponent } from '@shared/components/skeleton/loading-region.component';
import { PageHeaderSkeletonComponent } from '@shared/components/skeleton/page-header-skeleton.component';
import { SkeletonComponent } from '@shared/components/skeleton/skeleton.component';
import {
  skeletonIndexes,
  TableSkeletonComponent,
} from '@shared/components/skeleton/table-skeleton.component';
import {
  StatusStepperComponent,
  type StatusStepperStep,
} from '@shared/components/status-stepper/status-stepper.component';

import { isClosed, PIPELINE_STAGES, stageIndex } from '@/app/model/analysis-pipeline';
import type { Quote } from '@/app/model/quote';
import { stageOfQuote } from '@/app/model/quote-presentation';
import type { Report } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import {
  REPORT_TABLE_COLUMN_COUNT,
  ReportTableComponent,
} from '../report-table/report-table.component';

const ETAPAS: readonly StatusStepperStep[] = PIPELINE_STAGES.map((stage) => ({
  key: stage.key,
  label: stage.label,
  icon: stage.icon,
}));

const MINIMUM_ROWS = 5;

const STEP_PLACEHOLDER_COUNT = 5;

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
    ReportTableComponent,
    LoadingRegionComponent,
    PageHeaderSkeletonComponent,
    SkeletonComponent,
    TableSkeletonComponent,
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
  protected readonly stepPlaceholders = skeletonIndexes(STEP_PLACEHOLDER_COUNT);
  protected readonly reportColumnCount = REPORT_TABLE_COLUMN_COUNT;
  protected readonly minimumRows = MINIMUM_ROWS;
  protected readonly searchTerm = signal('');
  protected readonly quote = signal<Quote | null>(null);
  protected readonly reportList = signal<readonly Report[] | null>(null);
  protected readonly query = signal(unmeasuredQuery());
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly reportsLoading = signal(true);
  protected readonly testType = signal<string | null>(null);
  protected readonly summary = signal<string | null>(null);
  protected readonly failed = signal(false);

  protected readonly stage = computed(() => {
    const quote = this.quote();
    return quote ? stageOfQuote(quote) : null;
  });

  protected readonly currentStageIndex = computed(() => {
    const stage = this.stage();
    return stage && !isClosed(stage) ? stageIndex(stage) : null;
  });

  protected readonly breadcrumbs = computed<readonly Breadcrumb[]>(() => [
    { label: 'Relatórios', route: '/cliente/relatorios' },
    { label: this.quote()?.code ?? 'Orçamento' },
  ]);

  protected readonly withoutReports = computed(
    () => this.reportList() !== null && this.total() === 0,
  );

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
        this.reportsLoading.set(true);
        this.total.set(0);
        this.searchTerm.set('');
        this.query.update((current) => ({ ...current, page: 1 }));
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
      tap((loaded) => {
        this.quote.set(loaded);
        this.loading.set(false);
      }),
    );

    const measuredQuery = toObservable(this.query).pipe(
      filter(isMeasured),
      distinctUntilChanged(sameQuery),
    );

    combineLatest([quote, measuredQuery])
      .pipe(
        tap(() => this.reportsLoading.set(true)),
        switchMap(([loaded, query]) =>
          // Só o ID: a API resolve o orçamento autorizado e o liga ao AutoLAB pelo código da
          // cotação. O `code` exibido não é a chave do laboratório.
          this.reports.list({ quoteId: loaded.id, ...query }).pipe(
            catchError(() => {
              this.failed.set(true);
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
        this.reportsLoading.set(false);
      });
  }
}
