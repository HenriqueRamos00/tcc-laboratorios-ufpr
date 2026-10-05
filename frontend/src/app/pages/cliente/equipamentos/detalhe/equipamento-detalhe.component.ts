import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, finalize, of, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import {
  ROTULO_DO_TIPO_DE_ANALISE,
  type DetalheDoEquipamento,
  type RelatorioDoEquipamento,
} from '@/app/model/equipment';
import { ABA_DO_TIPO_DE_ANALISE, type AbaDeIndicadores } from '@/app/model/health-navigation';
import { EquipmentsService } from '@/app/services/equipments.service';
import { ReportsService } from '@/app/services/reports.service';

interface CartaoDeInformacao {
  readonly icone: string;
  readonly rotulo: string;
  readonly valor: string;
}

/**
 * Uma linha do histórico já resolvida para a tela: o rótulo que se lê, a aba
 * que o link abre e o nome do arquivo que o download salva. O template não
 * precisa saber traduzir nada disso.
 */
interface LinhaDoHistorico {
  readonly id: string;
  readonly data: string;
  readonly tipoDeAnalise: string;
  readonly situacao: string;
  readonly aba: AbaDeIndicadores;
  readonly temArquivo: boolean;
  readonly nomeDoArquivo: string;
}

const POR_PAGINA = 4;

const FALHA_NO_DOWNLOAD =
  'Não foi possível baixar o relatório agora. Tente novamente em instantes.';

function linhaDoHistorico(relatorio: RelatorioDoEquipamento): LinhaDoHistorico {
  return {
    id: relatorio.id,
    data: relatorio.data,
    tipoDeAnalise: ROTULO_DO_TIPO_DE_ANALISE[relatorio.tipoDeAnalise],
    situacao: relatorio.situacao,
    aba: ABA_DO_TIPO_DE_ANALISE[relatorio.tipoDeAnalise],
    temArquivo: !!relatorio.urlDoRelatorio,
    nomeDoArquivo: `relatorio-${relatorio.tipoDeAnalise}-${relatorio.data}.pdf`,
  };
}

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
  private readonly reports = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly equipamento = signal<DetalheDoEquipamento | null>(null);
  protected readonly historico = signal<readonly RelatorioDoEquipamento[]>([]);
  protected readonly falhou = signal(false);
  protected readonly pagina = signal(0);

  /** Identificador do relatório cujo arquivo está sendo buscado agora. */
  protected readonly baixando = signal<string | null>(null);
  protected readonly falhaNoDownload = signal('');

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
      { icone: 'electric_meter', rotulo: 'Potência', valor: equipamento.potencia },
    ];
  });

  protected readonly totalDePaginas = computed(() =>
    Math.max(1, Math.ceil(this.historico().length / POR_PAGINA)),
  );

  protected readonly visiveis = computed<readonly LinhaDoHistorico[]>(() =>
    this.historico()
      .slice(this.pagina() * POR_PAGINA, (this.pagina() + 1) * POR_PAGINA)
      .map(linhaDoHistorico),
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
        this.falhaNoDownload.set('');
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

  protected baixar(linha: LinhaDoHistorico): void {
    this.baixando.set(linha.id);
    this.falhaNoDownload.set('');
    this.reports
      .baixarRelatorio(linha.id)
      .pipe(
        finalize(() => this.baixando.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (arquivo) => this.entregarAoNavegador(arquivo, linha.nomeDoArquivo),
        error: () => this.falhaNoDownload.set(FALHA_NO_DOWNLOAD),
      });
  }

  // O navegador ainda resolve o href depois do clique, então a revogação espera
  // a próxima volta do laço de eventos: revogar na mesma volta cancela o
  // download antes de ele começar. Sem revogar, o Blob fica preso em memória
  // até a aba fechar.
  private entregarAoNavegador(arquivo: Blob, nome: string): void {
    const endereco = URL.createObjectURL(arquivo);
    const ancora = document.createElement('a');
    ancora.href = endereco;
    ancora.download = nome;
    ancora.click();
    setTimeout(() => URL.revokeObjectURL(endereco));
  }
}
