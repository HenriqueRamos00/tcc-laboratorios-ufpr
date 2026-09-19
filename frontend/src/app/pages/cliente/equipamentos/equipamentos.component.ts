import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@shared/components/search-field/search-field.component';

import type { Equipamento } from '@/app/model/equipment';
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
  templateUrl: './equipamentos.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientEquipamentosComponent {
  private readonly equipments = inject(EquipmentsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly termoDeBusca = signal('');
  protected readonly carregando = signal(true);
  protected readonly falhou = signal(false);
  protected readonly equipamentos = signal<readonly Equipamento[]>([]);

  protected readonly visiveis = computed(() => {
    const termo = this.termoDeBusca().trim().toLowerCase();
    if (!termo) return this.equipamentos();
    return this.equipamentos().filter(
      (equipamento) =>
        equipamento.numeroDeSerie.toLowerCase().includes(termo) || equipamento.tag.toLowerCase().includes(termo),
    );
  });

  constructor() {
    this.equipments
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.equipamentos.set(lista);
          this.carregando.set(false);
        },
        error: () => {
          this.falhou.set(true);
          this.carregando.set(false);
        },
      });
  }
}
