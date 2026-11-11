import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';
import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
import { PaginationComponent } from '@shared/components/pagination/pagination.component';

import type { Equipment } from '@/app/model/equipment';
import { DEFAULT_PAGE_SIZE } from '@/app/model/page';
import { EquipmentsService } from '@/app/services/equipments.service';

@Component({
  selector: 'app-client-equipamentos',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    PageHeaderComponent,
    SearchFieldComponent,
    EmptyStateComponent,
    DataUnavailableComponent,
    PaginationComponent,
  ],
  templateUrl: './equipment-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentListComponent {
  private readonly equipments = inject(EquipmentsService);

  protected readonly pageSize = DEFAULT_PAGE_SIZE;
  protected readonly page = signal(1);
  protected readonly total = signal(0);
  protected readonly searchTerm = signal('');
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly unavailable = signal(false);
  protected readonly equipmentList = signal<readonly Equipment[]>([]);

  /**
   * A busca é por número de série e por TAG, como o campo promete. São três campos porque
   * TAG tem duas procedências: a de placa, que o cadastro informa em 55% das linhas, e a
   * identificação do laudo, que é o que `tag` carrega quando o cadastro não informa. Buscar
   * só em `tag` deixaria de encontrar pela TAG de placa, que é justamente a que o cliente
   * tem pintada no equipamento e vê impressa na coluna.
   */
  protected readonly visible = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.equipmentList();
    return this.equipmentList().filter(
      (equipment) =>
        (equipment.serialNumber ?? '').toLowerCase().includes(term) ||
        (equipment.equipmentTag ?? '').toLowerCase().includes(term) ||
        equipment.tag.toLowerCase().includes(term),
    );
  });

  constructor() {
    toObservable(this.page)
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.failed.set(false);
          this.unavailable.set(false);
        }),
        switchMap((page) =>
          this.equipments.list({ page, pageSize: this.pageSize }).pipe(
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

  protected goToPage(page: number): void {
    this.page.set(page);
  }
}
