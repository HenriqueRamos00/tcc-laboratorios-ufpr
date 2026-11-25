import { BreakpointObserver } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  contentChildren,
  effect,
  ElementRef,
  inject,
  input,
  model,
  signal,
  type TemplateRef,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';

import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
import { SkeletonPlaceholderDirective } from '@shared/components/skeleton/skeleton-placeholder.directive';
import { SkeletonComponent } from '@shared/components/skeleton/skeleton.component';
import {
  skeletonCellWidthClass,
  skeletonIndexes,
} from '@shared/components/skeleton/table-skeleton.component';
import {
  PaginationComponent,
  type PageSizeOption,
} from '@shared/components/pagination/pagination.component';
import { viewportRows } from '@shared/viewport-rows';

import { DataTableActionsDirective } from './data-table-actions.directive';
import { DataTableCellDirective } from './data-table-cell.directive';
import {
  pageKeepingFirstRow,
  unmeasuredQuery,
  type DataTableCellValue,
  type DataTableColumn,
  type DataTableQuery,
  type DataTableRowContext,
} from './data-table.model';

const AUTOMATIC = 'auto';

const COMPACT_QUERY = '(max-width: 767.98px)';

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    MatIconModule,
    DataUnavailableComponent,
    PaginationComponent,
    SkeletonComponent,
    SkeletonPlaceholderDirective,
  ],
  templateUrl: './data-table.component.html',
  host: { class: 'block min-w-0' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataTableComponent<T> {
  readonly rows = input.required<readonly T[]>();
  readonly columns = input.required<readonly DataTableColumn<T>[]>();
  readonly caption = input.required<string>();
  readonly total = input(0);
  readonly loading = input(false);
  readonly trackBy = input<(row: T) => unknown>((row) => row);
  readonly rowHeight = input(56);
  readonly minimumRows = input(1);
  readonly pageSizeOptions = input<readonly number[]>([10, 25, 50, 100]);
  readonly paginationLabel = input('Paginação');
  readonly actionsHeader = input('Ações');
  readonly loadingMessage = input('Carregando…');
  readonly emptyIcon = input('inbox');
  readonly emptyTitle = input('Nenhum registro encontrado');
  readonly emptyDescription = input<string>();

  readonly query = model<DataTableQuery>(unmeasuredQuery());

  protected readonly actions = contentChild<DataTableActionsDirective<T>>(DataTableActionsDirective);

  private readonly cells = contentChildren<DataTableCellDirective<T>>(DataTableCellDirective);
  private readonly root = viewChild.required<ElementRef<HTMLElement>>('root');
  private readonly head = viewChild<ElementRef<HTMLElement>>('head');
  private readonly footer = viewChild<ElementRef<HTMLElement>>('footer');

  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly compact = toSignal(
    this.breakpoints.observe(COMPACT_QUERY).pipe(map((state) => state.matches)),
    { initialValue: this.breakpoints.isMatched(COMPACT_QUERY) },
  );

  private readonly measuredRows = viewportRows({
    host: () => this.root().nativeElement,
    reservedHeight: () => this.reservedHeight(),
    rowHeight: () => this.renderedRowHeight(),
    minimum: () => this.minimumRows(),
  });

  private readonly fittingRows = computed(() =>
    this.compact() ? this.pageSizeOptions()[0] : this.measuredRows(),
  );

  private readonly measured = signal(false);
  private readonly chosenPageSize = computed(() => this.query().chosenPageSize ?? null);

  protected readonly pageSize = computed(() => this.chosenPageSize() ?? this.fittingRows());

  protected readonly pageSizeChoice = computed(() => String(this.chosenPageSize() ?? AUTOMATIC));

  protected readonly pageSizeChoices = computed<readonly PageSizeOption[]>(() => [
    { value: AUTOMATIC, label: `Auto (${this.fittingRows()})` },
    ...this.pageSizeOptions().map((size) => ({ value: String(size), label: String(size) })),
  ]);

  protected readonly columnCount = computed(
    () => this.columns().length + (this.actions() ? 1 : 0),
  );

  protected readonly skeletonRows = computed(() =>
    this.loading() && this.rows().length === 0
      ? skeletonIndexes(Math.min(this.pageSize(), this.fittingRows(), this.compact() ? 3 : Infinity))
      : [],
  );

  private readonly cellTemplates = computed(
    () => new Map(this.cells().map((cell) => [cell.key(), cell.template])),
  );

  constructor() {
    afterNextRender({ read: () => this.measured.set(true) });

    effect(() => {
      const pageSize = this.pageSize();
      if (!this.measured()) return;

      untracked(() => {
        const current = this.query();
        if (current.pageSize === pageSize) return;
        this.query.set({ ...current, page: pageKeepingFirstRow(current, pageSize), pageSize });
      });
    });
  }

  protected cellTemplate(key: string): TemplateRef<DataTableRowContext<T>> | undefined {
    return this.cellTemplates().get(key);
  }

  protected cellValue(row: T, column: DataTableColumn<T>): DataTableCellValue {
    if (column.value) return column.value(row);
    const value = (row as Record<string, unknown>)[column.key];
    return typeof value === 'string' || typeof value === 'number' ? value : null;
  }

  protected skeletonWidthClass(row: number, column: number): string {
    return skeletonCellWidthClass(row, column);
  }

  protected goToPage(page: number): void {
    this.query.update((current) => ({ ...current, page }));
  }

  protected choosePageSize(choice: string): void {
    this.query.update((current) => ({
      ...current,
      chosenPageSize: choice === AUTOMATIC ? null : Number(choice),
    }));
  }

  private reservedHeight(): number {
    const root = this.root().nativeElement;
    const borders = root.offsetHeight - root.clientHeight;
    return Math.ceil(
      (this.head()?.nativeElement.getBoundingClientRect().height ?? 0) +
        (this.footer()?.nativeElement.getBoundingClientRect().height ?? 0) +
        borders,
    );
  }

  private renderedRowHeight(): number {
    const row = this.root().nativeElement.querySelector<HTMLElement>('tbody tr[data-row]');
    return row?.getBoundingClientRect().height || this.rowHeight();
  }
}
