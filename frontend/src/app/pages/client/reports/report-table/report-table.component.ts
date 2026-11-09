import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';

import { readableDate } from '@shared/brazilian-date';
import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
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
  readonly hasSignedDocument: boolean;
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
    publishedOn: published ? readableDate(published) : NOT_REPORTED,
    hasSignedDocument: report.hasSignedDocument,
    fileName: `relatorio-${(report.identification ?? report.id).replace(/[^\w.-]+/g, '-')}.pdf`,
  };
}

@Component({
  selector: 'app-report-table',
  standalone: true,
  imports: [MatIconModule, DataUnavailableComponent],
  host: { class: 'block' },
  template: `
    <div class="overflow-hidden rounded-2xl border border-lactec-line bg-lactec-paper">
      <div class="overflow-x-auto">
        <table class="w-full border-collapse text-sm">
          <caption class="sr-only">{{ caption() }}</caption>
          <thead>
            <tr class="bg-lactec-line-soft text-left">
              <th scope="col" class="px-4 py-3 font-semibold text-lactec-ink">Relatório</th>
              <th scope="col" class="px-4 py-3 font-semibold text-lactec-ink">Solicitação</th>
              <th scope="col" class="px-4 py-3 font-semibold text-lactec-ink">Amostra</th>
              <th scope="col" class="px-4 py-3 font-semibold text-lactec-ink">Laboratório</th>
              <th scope="col" class="px-4 py-3 font-semibold text-lactec-ink">Situação</th>
              <th scope="col" class="px-4 py-3 font-semibold text-lactec-ink">Publicação</th>
              <th scope="col" class="px-4 py-3 text-right font-semibold text-lactec-ink">
                Download
              </th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.id) {
              <tr class="border-t border-lactec-line-soft">
                <td class="px-4 py-3 text-lactec-ink">{{ row.identification }}</td>
                <td class="px-4 py-3 text-lactec-ink">{{ row.requestNumber }}</td>
                <td class="px-4 py-3 text-lactec-ink">{{ row.sample }}</td>
                <td class="px-4 py-3 text-lactec-ink"><app-data-unavailable /></td>
                <td class="px-4 py-3 text-lactec-ink">{{ row.status }}</td>
                <td class="px-4 py-3 text-lactec-ink">{{ row.publishedOn }}</td>
                <td class="px-4 py-3 text-right">
                  <button
                    type="button"
                    class="inline-flex h-10 w-10 items-center justify-center rounded-lg
                           bg-lactec-nav text-lactec-on-primary hover:bg-lactec-nav-dark
                           disabled:opacity-40"
                    [disabled]="!row.hasSignedDocument || downloading() === row.id"
                    [attr.aria-label]="
                      row.hasSignedDocument
                        ? 'Baixar o relatório ' + row.identification
                        : 'O relatório ' + row.identification + ' ainda não tem PDF assinado'
                    "
                    [attr.aria-busy]="downloading() === row.id ? 'true' : null"
                    (click)="download(row)"
                  >
                    <mat-icon aria-hidden="true">
                      {{ downloading() === row.id ? 'hourglass_empty' : 'download' }}
                    </mat-icon>
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    @if (downloadFailure()) {
      <p class="mt-4 text-sm text-lactec-danger" role="alert">{{ downloadFailure() }}</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportTableComponent {
  readonly reports = input.required<readonly Report[]>();
  readonly caption = input('Relatórios publicados.');

  private readonly reportsService = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly downloading = signal<string | null>(null);
  protected readonly downloadFailure = signal('');

  protected readonly rows = computed<readonly ReportRow[]>(() => this.reports().map(reportRow));

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
