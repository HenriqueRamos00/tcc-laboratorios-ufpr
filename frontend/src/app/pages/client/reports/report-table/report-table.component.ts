import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  model,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';

import { DataPipe } from '@shared/pipes/data.pipe';
import { DataTableActionsDirective } from '@shared/components/data-table/data-table-actions.directive';
import { DataTableComponent } from '@shared/components/data-table/data-table.component';
import {
  unmeasuredQuery,
  type DataTableColumn,
  type DataTableQuery,
} from '@shared/components/data-table/data-table.model';
import { handFileToTheBrowser } from '@shared/file-download';

import type { Report } from '@/app/model/report';
import { ReportsService } from '@/app/services/reports.service';

const NOT_REPORTED = 'Não informado';

const DOWNLOAD_FAILURE =
  'Não foi possível baixar o relatório agora. Tente novamente em instantes.';

interface ReportRow {
  readonly id: string;
  readonly identification: string;
  readonly requestNumber: string;
  readonly sample: string;
  readonly status: string;
  readonly publishedOn: string;
  readonly laboratory: string | null;
  readonly pdfAvailable: boolean;
  readonly fileName: string;
}

function reportRow(report: Report): ReportRow {
  const sample = [report.sampleId, report.sampleName].filter((part) => !!part?.trim()).join(' · ');
  const published = report.publicationDate ?? report.issueDate;
  return {
    id: report.id,
    identification: report.identification ?? NOT_REPORTED,
    requestNumber: report.requestNumber ?? NOT_REPORTED,
    sample: sample || NOT_REPORTED,
    status: report.status ?? NOT_REPORTED,
    publishedOn: published ? dataPipe.transform(published) : NOT_REPORTED,
    pdfAvailable: report.pdfAvailable ?? report.hasSignedDocument,
    laboratory: report.laboratory?.trim() || null,
    fileName: `relatorio-${(report.identification ?? report.id).replace(/[^\w.-]+/g, '-')}.pdf`,
  };
}

const COLUMNS: readonly DataTableColumn<ReportRow>[] = [
  { key: 'identification', header: 'Relatório' },
  { key: 'requestNumber', header: 'Solicitação' },
  { key: 'sample', header: 'Amostra' },
  { key: 'laboratory', header: 'Laboratório' },
  { key: 'status', header: 'Situação' },
  { key: 'publishedOn', header: 'Publicação' },
];

export const REPORT_TABLE_COLUMN_COUNT = COLUMNS.length + 1;

const dataPipe = new DataPipe();

@Component({
  selector: 'app-report-table',
  standalone: true,
  imports: [MatIconModule, DataTableComponent, DataTableActionsDirective],
  host: { class: 'block' },
  template: `
    <app-data-table
      actionsHeader="Download"
      loadingMessage="Carregando relatórios…"
      emptyIcon="search_off"
      [caption]="caption()"
      [paginationLabel]="paginationLabel()"
      [emptyTitle]="emptyTitle()"
      [emptyDescription]="emptyDescription()"
      [columns]="columns"
      [rows]="rows()"
      [trackBy]="trackById"
      [total]="total()"
      [loading]="loading()"
      [minimumRows]="minimumRows()"
      [(query)]="query"
    >
      <ng-template
        appDataTableActions
        [appDataTableActionsOf]="rows()"
        let-row
        let-compact="compact"
      >
        <button
          type="button"
          class="inline-flex h-10 items-center justify-center gap-2 rounded-lg
                 bg-lactec-nav text-lactec-on-primary hover:bg-lactec-nav-dark
                 disabled:opacity-40"
          [class.w-10]="!compact"
          [class.h-11]="compact"
          [class.w-full]="compact"
          [class.px-4]="compact"
          [disabled]="!row.pdfAvailable || downloading() === row.id"
          [attr.aria-label]="
            row.pdfAvailable
              ? 'Baixar o relatório ' + row.identification
              : 'O relatório ' + row.identification + ' ainda não tem PDF disponível'
          "
          [attr.aria-busy]="downloading() === row.id ? 'true' : null"
          (click)="download(row)"
        >
          <mat-icon aria-hidden="true">
            {{ downloading() === row.id ? 'hourglass_empty' : 'download' }}
          </mat-icon>
          @if (compact) {
            <span class="text-sm font-semibold" aria-hidden="true">
              {{ row.pdfAvailable ? 'Baixar relatório' : 'PDF indisponível' }}
            </span>
          }
        </button>
      </ng-template>
    </app-data-table>

    @if (downloadFailure()) {
      <p class="mt-4 text-sm text-lactec-danger" role="alert">{{ downloadFailure() }}</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportTableComponent {
  readonly reports = input.required<readonly Report[]>();
  readonly caption = input('Relatórios publicados.');
  readonly paginationLabel = input('Paginação dos relatórios');
  readonly emptyTitle = input('Nenhum relatório encontrado');
  readonly emptyDescription = input<string>();
  readonly total = input(0);
  readonly loading = input(false);
  readonly minimumRows = input(1);
  readonly query = model<DataTableQuery>(unmeasuredQuery());

  private readonly reportsService = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = COLUMNS;
  protected readonly downloading = signal<string | null>(null);
  protected readonly downloadFailure = signal('');

  protected readonly rows = computed<readonly ReportRow[]>(() => this.reports().map(reportRow));

  protected readonly trackById = (row: ReportRow): string => row.id;

  protected download(row: ReportRow): void {
    this.downloading.set(row.id);
    this.downloadFailure.set('');
    this.reportsService
      .signedDocument(row.id)
      .pipe(
        finalize(() => this.downloading.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (file) => handFileToTheBrowser(file.content, file.name ?? row.fileName),
        error: () => this.downloadFailure.set(DOWNLOAD_FAILURE),
      });
  }
}
