import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  standalone: true,
  host: { class: 'block' },
  template: `
    <div
      class="flex flex-wrap items-center justify-end gap-3"
      role="group"
      [attr.aria-label]="label()"
    >
      <span class="text-sm text-lactec-muted" aria-live="polite">{{ range() }}</span>
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
  readonly page = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly total = input.required<number>();
  readonly label = input('Paginação');
  readonly disabled = input(false);
  readonly pageChange = output<number>();

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
}
