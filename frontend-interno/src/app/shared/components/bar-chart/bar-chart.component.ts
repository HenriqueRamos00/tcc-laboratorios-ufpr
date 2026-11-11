import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface BarChartRow {
  readonly label: string;
  readonly values: readonly number[];
}

export interface BarChartSeries {
  readonly label: string;
  readonly tone: 'primary' | 'navy';
}

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  template: `
    <div role="group" [attr.aria-label]="label()">
      <ul class="mb-5 flex list-none flex-wrap gap-x-5 gap-y-2 p-0 text-sm text-lactec-muted" aria-label="Legenda">
        @for (item of series(); track item.label) {
          <li class="inline-flex items-center gap-2">
            <span class="h-3 w-3 shrink-0 rounded-sm" aria-hidden="true"
              [class.bg-lactec-primary]="item.tone === 'primary'"
              [class.bg-lactec-nav]="item.tone === 'navy'"></span>
            {{ item.label }}
          </li>
        }
      </ul>
      <dl class="m-0 space-y-4">
        @for (row of rows(); track row.label) {
          <div class="grid min-w-0 grid-cols-[6.5rem_minmax(0,1fr)] items-center gap-3">
            <dt class="text-sm text-lactec-ink-soft">{{ row.label }}</dt>
            <dd class="m-0 min-w-0 space-y-1.5">
              @for (value of row.values; track $index; let index = $index) {
                <div class="grid min-w-0 grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
                  <span class="sr-only">{{ series()[index].label }}:</span>
                  <div class="h-3 min-w-0 rounded-sm bg-lactec-line-soft" aria-hidden="true">
                    <div class="h-full rounded-sm"
                      [class.bg-lactec-primary]="series()[index].tone === 'primary'"
                      [class.bg-lactec-nav]="series()[index].tone === 'navy'"
                      [style.width.%]="value / scale() * 100"></div>
                  </div>
                  <span class="min-w-6 text-right text-sm tabular-nums text-lactec-ink">{{ value | number: '1.0-0' }}</span>
                </div>
              }
            </dd>
          </div>
        }
      </dl>
    </div>
  `,
})
export class BarChartComponent {
  readonly label = input.required<string>();
  readonly rows = input.required<readonly BarChartRow[]>();
  readonly series = input.required<readonly BarChartSeries[]>();
  readonly scale = computed(() => Math.max(1, ...this.rows().flatMap((row) => row.values)));
}
