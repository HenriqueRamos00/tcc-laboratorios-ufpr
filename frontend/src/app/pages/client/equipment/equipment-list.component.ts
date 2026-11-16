import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
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
  skip,
  switchMap,
  tap,
} from 'rxjs';

import { DataTableActionsDirective } from '@shared/components/data-table/data-table-actions.directive';
import { DataTableComponent } from '@shared/components/data-table/data-table.component';
import {
  isMeasured,
  sameQuery,
  unmeasuredQuery,
  type DataTableColumn,
} from '@shared/components/data-table/data-table.model';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';

import type { Equipment } from '@/app/model/equipment';
import {
  EQUIPMENT_SEARCH_MAX_LENGTH,
  EquipmentsService,
} from '@/app/services/equipments.service';

export const SEARCH_DEBOUNCE_MS = 300;

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
];

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
  ],
  templateUrl: './equipment-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentListComponent {
  private readonly equipments = inject(EquipmentsService);

  protected readonly columns = COLUMNS;
  protected readonly searchMaxLength = EQUIPMENT_SEARCH_MAX_LENGTH;
  protected readonly query = signal(unmeasuredQuery());
  protected readonly total = signal(0);
  protected readonly searchTerm = signal('');
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly unavailable = signal(false);
  protected readonly equipmentList = signal<readonly Equipment[]>([]);

  private readonly appliedSearch = signal('');

  private readonly request = computed(() => ({ ...this.query(), search: this.appliedSearch() }));

  protected readonly emptyDescription = computed(() =>
    this.appliedSearch() ? 'Revise o número de série ou a TAG informada.' : undefined,
  );

  protected readonly trackById = (equipment: Equipment): string => equipment.id;

  constructor() {
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

    toObservable(this.request)
      .pipe(
        filter((request) => isMeasured(request)),
        distinctUntilChanged(
          (previous, current) => sameQuery(previous, current) && previous.search === current.search,
        ),
        tap(() => {
          this.loading.set(true);
          this.failed.set(false);
          this.unavailable.set(false);
        }),
        switchMap((request) =>
          this.equipments.list(request).pipe(
            catchError((error: unknown) => {
              this.unavailable.set(error instanceof HttpErrorResponse && error.status === 501);
              this.failed.set(true);
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
      });
  }
}
