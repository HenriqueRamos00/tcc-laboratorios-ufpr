import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { DataPipe } from '@shared/pipes/data.pipe';

import type { Classification, PublishedReport } from '@/app/model/health';

const RESULT_COLOR_PAIR: Record<Classification, string> = {
  normal: 'bg-lactec-tarja-normal text-lactec-on-tarja-normal',
  alerta: 'bg-lactec-tarja-alerta text-lactec-on-tarja-alerta',
  'nao-conforme': 'bg-lactec-tarja-nao-conforme text-lactec-on-tarja-nao-conforme',
};

const RESULT_WITHOUT_COLOR = 'border border-lactec-line text-lactec-ink';

interface ReportEntry extends PublishedReport {
  readonly readableDate: string;
  readonly resultColorPair: string;
}

const dataPipe = new DataPipe();

@Component({
  selector: 'app-published-reports',
  standalone: true,
  template: `
    <h2
      [id]="headingId()"
      class="font-display tracking-display m-0 text-base font-semibold text-lactec-ink uppercase"
    >
      Laudos
    </h2>
    @if (entries().length) {
      <ol class="m-0 mt-2 list-none divide-y divide-lactec-line-soft p-0">
        @for (report of entries(); track report.identification) {
          <li class="py-4 first:pt-2 last:pb-0">
            <article [attr.aria-label]="report.identification">
              <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-lactec-ink-soft">
                <span class="font-semibold text-lactec-ink">{{ report.readableDate }}</span>
                <span>{{ report.identification }}</span>
                @if (report.resultLabel; as label) {
                  <span
                    data-result-label
                    class="font-display tracking-display px-2 py-0.5 text-xs font-semibold uppercase"
                    [class]="report.resultColorPair"
                  >
                    {{ label }}
                  </span>
                }
              </p>
              @if (report.title; as title) {
                <h3 class="m-0 mt-2 text-sm font-semibold text-lactec-ink uppercase">{{ title }}</h3>
              }
              @for (paragraph of report.paragraphs; track $index) {
                <p class="mt-2 mb-0 text-sm text-lactec-ink-soft">{{ paragraph }}</p>
              }
            </article>
          </li>
        }
      </ol>
    } @else {
      <p class="mt-2 mb-0 text-sm text-lactec-muted">Nenhum laudo publicado</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublishedReportsComponent {
  readonly reports = input.required<readonly PublishedReport[]>();
  readonly headingId = input.required<string>();

  protected readonly entries = computed<readonly ReportEntry[]>(() =>
    this.reports().map((report) => ({
      ...report,
      readableDate: dataPipe.transform(report.date),
      resultColorPair: report.conformity ? RESULT_COLOR_PAIR[report.conformity] : RESULT_WITHOUT_COLOR,
    })),
  );
}
