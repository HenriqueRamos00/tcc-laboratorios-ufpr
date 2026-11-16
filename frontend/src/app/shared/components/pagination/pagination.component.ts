import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PAGINATION_TOUR_TARGET } from './pagination.tour';

export interface PageSizeOption {
  readonly value: string;
  readonly label: string;
}

@Component({
  selector: 'app-pagination',
  standalone: true,
  host: { class: 'block' },
  template: `
    <div
      class="flex flex-wrap items-center justify-end gap-3"
      role="group"
      [attr.aria-label]="label()"
      [attr.data-tour]="tourTarget.pages"
    >
      @if (pageSizeOptions().length) {
        <label
          class="mr-auto flex items-center gap-2 text-sm text-lactec-muted"
          [attr.data-tour]="tourTarget.pageSize"
        >
          Linhas por página
          <select
            class="h-10 rounded-lg border border-lactec-field-border bg-lactec-paper px-2 text-sm
                   text-lactec-ink hover:border-lactec-field-border-hover focus:border-lactec-field-border-focus focus:outline-none
                   focus:ring-1 focus:ring-lactec-field-border-focus disabled:opacity-40"
            [disabled]="disabled()"
            (change)="choosePageSize($event)"
          >
            @for (option of pageSizeOptions(); track option.value) {
              <option [value]="option.value" [selected]="option.value === pageSizeChoice()">
                {{ option.label }}
              </option>
            }
          </select>
        </label>
      }
      <span class="text-sm tabular-nums text-lactec-muted" aria-live="polite">{{ range() }}</span>
      <button
        type="button"
        class="rounded-lg border border-lactec-line px-3 py-2 text-sm text-lactec-ink
               disabled:opacity-40"
        [disabled]="disabled() || page() <= 1"
        (click)="pageChange.emit(page() - 1)"
      >
        Anterior
      </button>
      <button
        type="button"
        class="rounded-lg border border-lactec-line px-3 py-2 text-sm text-lactec-ink
               disabled:opacity-40"
        [disabled]="disabled() || page() >= totalPages()"
        (click)="pageChange.emit(page() + 1)"
      >
        Próximo
      </button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  protected readonly tourTarget = PAGINATION_TOUR_TARGET;
  readonly page = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly total = input.required<number>();
  readonly label = input('Paginação');
  readonly disabled = input(false);
  readonly pageSizeOptions = input<readonly PageSizeOption[]>([]);
  readonly pageSizeChoice = input('');
  readonly pageChange = output<number>();
  readonly pageSizeChoiceChange = output<string>();

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.total() / Math.max(1, this.pageSize()))),
  );

  protected readonly range = computed(() => {
    const total = this.total();
    if (!total) return '0 de 0';
    const from = (this.page() - 1) * this.pageSize() + 1;
    const to = Math.min(from + this.pageSize() - 1, total);
    return `${from}-${to} de ${total}`;
  });

  protected choosePageSize(event: Event): void {
    this.pageSizeChoiceChange.emit((event.target as HTMLSelectElement).value);
  }
}
