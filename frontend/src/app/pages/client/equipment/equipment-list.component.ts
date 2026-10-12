import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';

import type { Equipment } from '@/app/model/equipment';
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
  ],
  templateUrl: './equipment-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentListComponent {
  private readonly equipments = inject(EquipmentsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly searchTerm = signal('');
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly equipmentList = signal<readonly Equipment[]>([]);

  /**
   * A busca é por número de série e por TAG, como o campo promete. O número de série pode
   * ser nulo no dado real, e aí sobra a TAG, que nunca é nula.
   */
  protected readonly visible = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.equipmentList();
    return this.equipmentList().filter(
      (equipment) =>
        (equipment.serialNumber ?? '').toLowerCase().includes(term) ||
        equipment.tag.toLowerCase().includes(term),
    );
  });

  constructor() {
    this.equipments
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.equipmentList.set(list);
          this.loading.set(false);
        },
        error: () => {
          this.failed.set(true);
          this.loading.set(false);
        },
      });
  }
}
