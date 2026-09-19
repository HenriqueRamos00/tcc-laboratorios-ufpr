import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, of, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import type { DetalheDoEquipamento, RelatorioDoEquipamento } from '@/app/model/equipment';
import { EquipmentsService } from '@/app/services/equipments.service';

interface CartaoDeInformacao {
  readonly icone: string;
  readonly rotulo: string;
  readonly valor: string;
}

const POR_PAGINA = 4;

@Component({
  selector: 'app-equipamento-detalhe',
  standalone: true,
  imports: [RouterLink, MatIconModule, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './equipamento-detalhe.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipamentoDetalheComponent {
  /** Vem do parâmetro de rota, via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly equipments = inject(EquipmentsService);

  protected readonly equipamento = signal<DetalheDoEquipamento | null>(null);
  protected readonly historico = signal<readonly RelatorioDoEquipamento[]>([]);
  protected readonly falhou = signal(false);
  protected readonly pagina = signal(0);

  protected readonly cartoes = computed<readonly CartaoDeInformacao[]>(() => {
    const equipamento = this.equipamento();
    if (!equipamento) return [];
    return [
      { icone: 'tag', rotulo: 'Numero de Série', valor: equipamento.numeroDeSerie },
      { icone: 'sell', rotulo: 'TAG', valor: equipamento.tag },
      { icone: 'water_drop', rotulo: 'Tipo do Oleo', valor: equipamento.tipoDoOleo },
      { icone: 'location_on', rotulo: 'Local', valor: equipamento.localizacao },
      { icone: 'business_center', rotulo: 'Empresa', valor: equipamento.empresa },
      { icone: 'bolt', rotulo: 'Tensão', valor: equipamento.tensao },
      { icone: 'bolt', rotulo: 'Potência', valor: equipamento.potencia },
    ];
  });

  protected readonly totalDePaginas = computed(() =>
    Math.max(1, Math.ceil(this.historico().length / POR_PAGINA)),
  );

  protected readonly visiveis = computed(() =>
    this.historico().slice(this.pagina() * POR_PAGINA, (this.pagina() + 1) * POR_PAGINA),
  );

  protected readonly intervalo = computed(() => {
    const total = this.historico().length;
    if (!total) return '0 de 0';
    const de = this.pagina() * POR_PAGINA + 1;
    const ate = Math.min(de + POR_PAGINA - 1, total);
    return `${de}-${ate} de ${total}`;
  });

  constructor() {
    // O router reaproveita a instância quando só o id muda, e ngOnInit não roda
    // de novo.
    const id = toObservable(this.id);

    id.pipe(
      tap(() => {
        this.equipamento.set(null);
        this.falhou.set(false);
        this.pagina.set(0);
      }),
      switchMap((valor) =>
        this.equipments.buscarPorId(valor).pipe(
          catchError(() => {
            this.falhou.set(true);
            return EMPTY;
          }),
        ),
      ),
      takeUntilDestroyed(),
    ).subscribe((detalhe) => this.equipamento.set(detalhe));

    id.pipe(
      switchMap((valor) =>
        this.equipments.historicoDeRelatorios(valor).pipe(catchError(() => of([]))),
      ),
      takeUntilDestroyed(),
    ).subscribe((lista) => this.historico.set(lista));
  }

  protected anterior(): void {
    this.pagina.update((atual) => Math.max(0, atual - 1));
  }

  protected proximo(): void {
    this.pagina.update((atual) => Math.min(this.totalDePaginas() - 1, atual + 1));
  }
}
