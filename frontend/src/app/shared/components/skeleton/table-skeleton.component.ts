import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { SkeletonComponent } from './skeleton.component';

const CELL_WIDTH_CLASSES = ['w-24', 'w-32', 'w-20', 'w-28'] as const;

export function skeletonCellWidthClass(row: number, column: number): string {
  return CELL_WIDTH_CLASSES[(row + column) % CELL_WIDTH_CLASSES.length];
}

export function skeletonIndexes(count: number): readonly number[] {
  return Array.from({ length: Math.max(0, count) }, (unused, index) => index);
}

@Component({
  selector: 'app-table-skeleton',
  standalone: true,
  imports: [SkeletonComponent],
  host: { class: 'block min-w-0' },
  template: `
    <div class="overflow-hidden rounded-2xl border border-lactec-line bg-lactec-paper">
      <div class="flex h-11 items-center gap-8 bg-lactec-line-soft px-4">
        @for (column of columnIndexes(); track column) {
          <app-skeleton class="h-3.5 w-24" tone="strong" />
        }
      </div>
      @for (row of rowIndexes(); track row) {
        <div
          class="flex items-center gap-8 border-t border-lactec-line-soft px-4"
          [style.height.px]="rowHeight()"
        >
          @for (column of columnIndexes(); track column) {
            <app-skeleton class="h-4" [class]="widthClass(row, column)" />
          }
        </div>
      }
      <div
        class="flex min-h-16 items-center justify-end gap-3 border-t border-lactec-line-soft px-4 py-3"
      >
        <app-skeleton class="mr-auto h-10 w-44" />
        <app-skeleton class="h-4 w-20" />
        <app-skeleton class="h-9 w-20" />
        <app-skeleton class="h-9 w-20" />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableSkeletonComponent {
  readonly columns = input(4);
  readonly rows = input(5);
  readonly rowHeight = input(56);

  protected readonly columnIndexes = computed(() => skeletonIndexes(this.columns()));
  protected readonly rowIndexes = computed(() => skeletonIndexes(this.rows()));

  protected widthClass(row: number, column: number): string {
    return skeletonCellWidthClass(row, column);
  }
}
