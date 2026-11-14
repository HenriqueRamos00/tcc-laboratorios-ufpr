import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { NgClass } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';
import { PaginationComponent } from '@shared/components/pagination/pagination.component';

import {
  badgeFor,
  type DisplayedQuote,
  type StatusBadge,
  toDisplayedQuote,
} from '@/app/model/quote-presentation';
import { DEFAULT_PAGE_SIZE } from '@/app/model/page';
import type { Report } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { ReportTableComponent } from './report-table/report-table.component';

// A tela tem dois jeitos de achar um laudo, e nenhum dos dois acompanha etapa:
// quem já sabe o que procura varre todos os laudos publicados; quem veio pelo
// orçamento escolhe o orçamento e entra só nos laudos dele.
type ReportsTab = 'reports' | 'quotes';

@Component({
  selector: 'app-client-relatorios',
  standalone: true,
  imports: [
    NgClass,
    RouterLink,
    MatIconModule,
    PageHeaderComponent,
    SearchFieldComponent,
    EmptyStateComponent,
    PaginationComponent,
    ReportTableComponent,
  ],
  templateUrl: './report-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportListComponent {
  private readonly reportsService = inject(ReportsService);
  private readonly quotesService = inject(QuotesService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly activeTab = signal<ReportsTab>('reports');

  protected readonly reportSearch = signal('');
  protected readonly quoteSearch = signal('');

  protected readonly pageSize = DEFAULT_PAGE_SIZE;
  protected readonly reportPage = signal(1);
  protected readonly reportTotal = signal(0);
  protected readonly reportsLoading = signal(true);
  protected readonly reports = signal<readonly Report[] | null>(null);
  protected readonly quotesWithReport = signal<readonly DisplayedQuote[] | null>(null);
  // Os orçamentos vêm paginados da API; a aba mostra os da página carregada que
  // já têm laudo publicado, e a paginação percorre os demais.
  protected readonly quotePage = signal(1);
  protected readonly quoteTotal = signal(0);
  protected readonly quotesLoading = signal(true);
  protected readonly reportsFailed = signal(false);
  protected readonly quotesFailed = signal(false);

  protected readonly visibleReports = computed(() => {
    const list = this.reports() ?? [];
    const term = this.reportSearch().trim().toLowerCase();
    if (!term) return list;
    return list.filter((report) =>
      [report.identification, report.requestNumber, report.quoteCode].some((value) =>
        (value ?? '').toLowerCase().includes(term),
      ),
    );
  });

  protected readonly visibleQuotes = computed(() => {
    const list = this.quotesWithReport() ?? [];
    const term = this.quoteSearch().trim().toLowerCase();
    if (!term) return list;
    return list.filter(
      (quote) =>
        quote.code.toLowerCase().includes(term) ||
        quote.proposalName.toLowerCase().includes(term) ||
        quote.company.toLowerCase().includes(term),
    );
  });

  constructor() {
    toObservable(this.reportPage)
      .pipe(
        tap(() => {
          this.reportsLoading.set(true);
          this.reportsFailed.set(false);
        }),
        switchMap((page) =>
          this.reportsService.list({ page, pageSize: this.pageSize }).pipe(
            catchError(() => {
              this.reportsFailed.set(true);
              this.reportsLoading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.reports.set(result.items);
        this.reportTotal.set(result.total);
        this.reportsLoading.set(false);
      });

    toObservable(this.quotePage)
      .pipe(
        tap(() => {
          this.quotesLoading.set(true);
          this.quotesFailed.set(false);
        }),
        switchMap((page) =>
          this.quotesService.list({ page, pageSize: this.pageSize }).pipe(
            catchError(() => {
              this.quotesFailed.set(true);
              this.quotesLoading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.quotesWithReport.set(
          result.items
            .map(toDisplayedQuote)
            .filter((quote) => quote.status === 'relatorio-publicado'),
        );
        this.quoteTotal.set(result.total);
        this.quotesLoading.set(false);
      });
  }

  protected goToReportPage(page: number): void {
    this.reportPage.set(page);
  }

  protected goToQuotePage(page: number): void {
    this.quotePage.set(page);
  }

  protected selectTab(tab: ReportsTab): void {
    this.activeTab.set(tab);
  }

  protected badgeFor(status: DisplayedQuote['status']): StatusBadge {
    return badgeFor(status);
  }
}
