import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgClass } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';

import {
  badgeFor,
  type DisplayedQuote,
  type StatusBadge,
  toDisplayedQuote,
} from '@/app/model/quote-presentation';
import type { PublishedReport } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

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

  protected readonly reports = signal<readonly PublishedReport[] | null>(null);
  protected readonly quotesWithReport = signal<readonly DisplayedQuote[] | null>(null);
  protected readonly reportsFailed = signal(false);
  protected readonly quotesFailed = signal(false);

  protected readonly visibleReports = computed(() => {
    const list = this.reports() ?? [];
    const term = this.reportSearch().trim().toLowerCase();
    if (!term) return list;
    return list.filter(
      (report) =>
        report.code.toLowerCase().includes(term) || report.protocol.toLowerCase().includes(term),
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
    this.reportsService
      .publishedReports()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => this.reports.set(list),
        error: () => this.reportsFailed.set(true),
      });

    this.quotesService
      .getQuotes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (quotes) =>
          this.quotesWithReport.set(
            quotes
              .map(toDisplayedQuote)
              .filter((quote) => quote.status === 'relatorio-publicado'),
          ),
        error: () => this.quotesFailed.set(true),
      });
  }

  protected selectTab(tab: ReportsTab): void {
    this.activeTab.set(tab);
  }

  protected badgeFor(status: DisplayedQuote['status']): StatusBadge {
    return badgeFor(status);
  }
}
