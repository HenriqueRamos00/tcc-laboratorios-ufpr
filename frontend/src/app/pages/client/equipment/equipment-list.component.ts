import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  filter,
  map,
  merge,
  share,
  skip,
  Subject,
  type Subscription,
  switchMap,
  take,
  tap,
  timeout,
} from 'rxjs';

import { DataTableActionsDirective } from '@shared/components/data-table/data-table-actions.directive';
import { DataTableCellDirective } from '@shared/components/data-table/data-table-cell.directive';
import { DataTableComponent } from '@shared/components/data-table/data-table.component';
import {
  isMeasured,
  sameQuery,
  unmeasuredQuery,
  type DataTableColumn,
} from '@shared/components/data-table/data-table.model';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { EquipmentStatusComponent } from '@shared/components/equipment-status/equipment-status.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';

import {
  EQUIPMENT_STATUS_LABEL,
  EQUIPMENT_STATUS_ORDER,
  type Equipment,
  type EquipmentOverallStatus,
  type EquipmentStatus,
} from '@/app/model/equipment';
import {
  EQUIPMENT_SEARCH_MAX_LENGTH,
  EquipmentsService,
  EquipmentStatusUnavailable,
  STATUS_BATCH_LIMIT,
} from '@/app/services/equipments.service';

import { registerPageTour } from '@/app/services/tour.service';
import { equipmentListTour } from './equipment-list.tour';

export const SEARCH_DEBOUNCE_MS = 300;

export const NOTICE_WAIT_LIMIT_MS = 120_000;

export const SYNC_ALL_WAIT_LIMIT_MS = 900_000;

const NOT_REPORTED = 'Não informado';

const COLUMNS: readonly DataTableColumn<Equipment>[] = [
  {
    key: 'serialNumber',
    header: 'Número de Série',
    value: (equipment) => equipment.serialNumber ?? NOT_REPORTED,
  },
  { key: 'tag', header: 'TAG', value: (equipment) => equipment.equipmentTag ?? equipment.tag },
  {
    key: 'type',
    header: 'Tipo de Equipamento',
    value: (equipment) => equipment.type ?? NOT_REPORTED,
  },
  { key: 'reportCount', header: 'Laudos', value: (equipment) => equipment.reportCount },
  { key: 'status', header: 'Situação' },
];

const STATUS_OPTIONS = EQUIPMENT_STATUS_ORDER.map((value) => ({
  value,
  label: EQUIPMENT_STATUS_LABEL[value],
}));

@Component({
  selector: 'app-client-equipamentos',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    PageHeaderComponent,
    SearchFieldComponent,
    EmptyStateComponent,
    DataTableComponent,
    DataTableActionsDirective,
    DataTableCellDirective,
    EquipmentStatusComponent,
  ],
  templateUrl: './equipment-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentListComponent {
  private readonly equipments = inject(EquipmentsService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly notices = this.equipments.loadFinishedNotices().pipe(share());
  private readonly reloads = new Subject<void>();
  private readonly asked = new Set<string>();
  private watchingNotices = false;

  protected readonly columns = COLUMNS;
  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly statusFilter = signal<EquipmentOverallStatus | null>(null);
  protected readonly refreshing = signal<ReadonlySet<string>>(new Set());
  protected readonly refreshFailed = signal<ReadonlySet<string>>(new Set());
  protected readonly syncing = signal<ReadonlySet<string>>(new Set());
  protected readonly syncingAll = signal(false);
  protected readonly syncAllFailed = signal(false);
  protected readonly refreshUnavailable = signal(false);
  protected readonly searchMaxLength = EQUIPMENT_SEARCH_MAX_LENGTH;
  protected readonly query = signal(unmeasuredQuery());
  protected readonly total = signal(0);
  protected readonly searchTerm = signal('');
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly unavailable = signal(false);
  protected readonly equipmentList = signal<readonly Equipment[]>([]);

  private readonly appliedSearch = signal('');

  private readonly request = computed(() => ({
    ...this.query(),
    search: this.appliedSearch(),
    status: this.statusFilter(),
  }));

  protected readonly emptyDescription = computed(() => {
    if (this.statusFilter()) {
      return 'Só entram neste filtro os equipamentos que já tiveram os laudos lidos. Os demais aparecem na lista sem filtro e são atualizados ao serem exibidos.';
    }

    return this.appliedSearch() ? 'Revise o número de série ou a TAG informada.' : undefined;
  });

  protected readonly trackById = (equipment: Equipment): string => equipment.id;

  constructor() {
    registerPageTour(equipmentListTour);

    toObservable(this.searchTerm)
      .pipe(
        skip(1),
        map((term) => term.trim()),
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((term) => {
        this.appliedSearch.set(term);
        this.query.update((current) => ({ ...current, page: 1 }));
      });

    const chosen = toObservable(this.request).pipe(
      filter((request) => isMeasured(request)),
      distinctUntilChanged(
        (previous, current) =>
          sameQuery(previous, current)
          && previous.search === current.search
          && previous.status === current.status,
      ),
      map((request) => ({ request, silent: false })),
    );

    const reloaded = this.reloads.pipe(
      map(() => ({ request: this.request(), silent: true })),
      filter(({ request }) => isMeasured(request)),
    );

    merge(chosen, reloaded)
      .pipe(
        tap(({ silent }) => {
          if (silent) return;

          this.loading.set(true);
          this.failed.set(false);
          this.unavailable.set(false);
        }),
        switchMap(({ request, silent }) =>
          this.equipments.list(request).pipe(
            catchError((error: unknown) => {
              if (!silent) {
                this.unavailable.set(error instanceof HttpErrorResponse && error.status === 501);
                this.failed.set(true);
              }

              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.equipmentList.set(result.items);
        this.total.set(result.total);
        this.loading.set(false);
        this.syncVisible(result.items);
      });
  }

  protected syncAll(): void {
    if (this.syncingAll()) return;

    this.syncingAll.set(true);
    this.syncAllFailed.set(false);
    this.watchNotices();

    const waiting = this.afterTheNextNotice(SYNC_ALL_WAIT_LIMIT_MS, () => {
      this.asked.clear();
      this.syncingAll.set(false);
    });

    this.equipments
      .refreshAllStatuses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (answer) => {
          if (answer.accepted > 0) return;

          waiting.unsubscribe();
          this.syncingAll.set(false);
        },
        error: (error: unknown) => {
          waiting.unsubscribe();
          this.syncingAll.set(false);
          this.refreshUnavailable.set(error instanceof EquipmentStatusUnavailable);
          this.syncAllFailed.set(!(error instanceof EquipmentStatusUnavailable));
        },
      });
  }

  private syncVisible(items: readonly Equipment[]): void {
    if (this.refreshUnavailable()) return;

    const ids = items
      .filter((equipment) => equipment.status === null && !this.asked.has(equipment.id))
      .map((equipment) => equipment.id);

    if (ids.length === 0) return;

    this.watchNotices();

    for (let start = 0; start < ids.length; start += STATUS_BATCH_LIMIT) {
      this.askTheBatch(ids.slice(start, start + STATUS_BATCH_LIMIT));
    }
  }

  private askTheBatch(ids: readonly string[]): void {
    for (const id of ids) this.asked.add(id);

    this.syncing.update((current) => new Set([...current, ...ids]));

    const settle = (): void => this.syncing.update((current) => withoutAll(current, ids));
    const waiting = this.afterTheNextNotice(NOTICE_WAIT_LIMIT_MS, settle);

    this.equipments
      .refreshStatuses(ids)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (answer) => {
          if (answer.accepted > 0) return;

          waiting.unsubscribe();
          settle();
          this.reloads.next();
        },
        error: (error: unknown) => {
          waiting.unsubscribe();
          settle();
          this.refreshUnavailable.set(error instanceof EquipmentStatusUnavailable);
        },
      });
  }

  private watchNotices(): void {
    if (this.watchingNotices) return;

    this.watchingNotices = true;

    const stopped = (): void => {
      this.watchingNotices = false;
    };

    this.notices
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.reloads.next(), error: stopped, complete: stopped });
  }

  private afterTheNextNotice(limit: number, done: () => void): Subscription {
    let settled = false;
    const withoutNotice = (): void => {
      if (settled) return;

      settled = true;
      done();
      this.reloads.next();
    };

    return this.notices
      .pipe(take(1), timeout(limit), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          settled = true;
          done();
        },
        error: withoutNotice,
        complete: withoutNotice,
      });
  }

  protected chooseStatus(event: Event): void {
    const chosen = (event.target as HTMLSelectElement).value;
    this.statusFilter.set(chosen ? (chosen as EquipmentOverallStatus) : null);
    this.query.update((current) => ({ ...current, page: 1 }));
  }

  protected refresh(equipment: Equipment): void {
    const id = equipment.id;

    if (this.refreshing().has(id)) return;

    this.refreshing.update((current) => new Set(current).add(id));
    this.refreshFailed.update((current) => without(current, id));

    this.equipments
      .refreshStatus(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (result.alreadyRunning) {
            this.waitForTheNotice(id);
            return;
          }

          this.finishRefresh(id, result.status);
        },
        error: (error: unknown) => {
          this.refreshUnavailable.set(error instanceof EquipmentStatusUnavailable);
          this.failRefresh(id);
        },
      });
  }

  private waitForTheNotice(id: string): void {
    let asked = false;
    const askOnce = (): void => {
      if (asked) return;

      asked = true;
      this.askTheStatus(id);
    };

    this.notices
      .pipe(take(1), timeout(NOTICE_WAIT_LIMIT_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: askOnce, error: askOnce, complete: askOnce });
  }

  private askTheStatus(id: string): void {
    this.equipments
      .status(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => this.finishRefresh(id, status),
        error: () => this.failRefresh(id),
      });
  }

  private finishRefresh(id: string, status: EquipmentStatus | null): void {
    this.refreshing.update((current) => without(current, id));

    if (!status) {
      this.refreshFailed.update((current) => new Set(current).add(id));
      return;
    }

    this.equipmentList.update((list) =>
      list.map((equipment) => (equipment.id === id ? { ...equipment, status } : equipment)),
    );
  }

  private failRefresh(id: string): void {
    this.refreshing.update((current) => without(current, id));
    this.refreshFailed.update((current) => new Set(current).add(id));
  }
}

function withoutAll(ids: ReadonlySet<string>, gone: readonly string[]): ReadonlySet<string> {
  const rest = new Set(ids);
  for (const id of gone) rest.delete(id);
  return rest;
}

function without(ids: ReadonlySet<string>, id: string): ReadonlySet<string> {
  const rest = new Set(ids);
  rest.delete(id);
  return rest;
}
