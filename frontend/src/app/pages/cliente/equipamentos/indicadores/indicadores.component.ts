import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';

import { ClassificationBadgeComponent } from '@shared/components/classification-badge/classification-badge.component';
import { ConformityGaugeComponent } from '@shared/components/conformity-gauge/conformity-gauge.component';
import { DuvalPentagonComponent } from '@shared/components/duval-pentagon/duval-pentagon.component';
import { DuvalTriangleComponent } from '@shared/components/duval-triangle/duval-triangle.component';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import {
  LineChartComponent,
  type SerieDoGrafico,
} from '@shared/components/line-chart/line-chart.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import type { IndicadoresDeSaude, ResultadoDeRogers } from '@/app/model/health';
import { carregando, erro, ok, vazio, type RemoteData } from '@/app/model/remote-data';
import { EquipmentsService } from '@/app/services/equipments.service';
import { HealthService } from '@/app/services/health.service';

type Aba = 'fisico-quimico' | 'gases';
type SubAbaDeGases = 'criterios' | 'metodos';

const NOME_DO_ENSAIO: Record<string, string> = {
  neutralizacao: 'Índice Neutralização',
  agua: 'Teor de Água',
  densidade: 'Densidade a 20ºC',
  fatorDePotencia: 'Fator de Potência',
  rigidez: 'Rigidez Dielétrica',
  tensaoInterfacial: 'Tensão Interfacial',
  cor: 'Cor',
};

const NOME_DO_GAS: Record<string, string> = {
  h2: 'H₂',
  o2: 'O₂',
  n2: 'N₂',
  ch4: 'CH₄',
  co: 'CO',
  co2: 'CO₂',
  c2h4: 'C₂H₄',
  c2h6: 'C₂H₆',
  c2h2: 'C₂H₂',
};

const COR_DE_ROGERS: Record<ResultadoDeRogers, string> = {
  termico: '#1baf7a',
  descarga: '#2a78d6',
  indeterminado: 'transparent',
};

const ROTULO_DE_ROGERS: Record<ResultadoDeRogers, string> = {
  termico: 'Térmico',
  descarga: 'Descarga',
  indeterminado: 'Sem indicação',
};

// Legenda e marca leem a mesma constante: hex repetido no template faz a
// legenda mentir no dia em que a paleta mudar.
const LEGENDA_DE_ROGERS = (['termico', 'descarga'] as const).map((chave) => ({
  chave,
  rotulo: ROTULO_DE_ROGERS[chave],
  cor: COR_DE_ROGERS[chave],
}));

const COR_DA_SERIE_MEDIDA = '#2a78d6';
const COR_DO_LIMITE = '#1baf7a';

const LEGENDA_DA_NBR = [
  { chave: 'medido', rotulo: 'Medido', cor: COR_DA_SERIE_MEDIDA, formato: 'bloco' as const },
  { chave: 'limite', rotulo: 'Limite', cor: COR_DO_LIMITE, formato: 'linha' as const },
];

@Component({
  selector: 'app-indicadores',
  standalone: true,
  imports: [
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ConformityGaugeComponent,
    ClassificationBadgeComponent,
    LineChartComponent,
    DuvalTriangleComponent,
    DuvalPentagonComponent,
  ],
  templateUrl: './indicadores.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IndicadoresComponent {
  readonly id = input.required<string>();

  private readonly health = inject(HealthService);
  private readonly equipments = inject(EquipmentsService);

  protected readonly legendaDeRogers = LEGENDA_DE_ROGERS;
  protected readonly legendaDaNbr = LEGENDA_DA_NBR;
  protected readonly corDaSerieMedida = COR_DA_SERIE_MEDIDA;
  protected readonly corDoLimite = COR_DO_LIMITE;

  protected readonly abaAtiva = signal<Aba>('fisico-quimico');
  protected readonly subAba = signal<SubAbaDeGases>('criterios');
  // O router reaproveita a instância entre /equipamentos/1 e /2 e ngOnInit não
  // roda de novo; o switchMap externo cancela a busca anterior. startWith e
  // catchError ficam no pipe INTERNO: assim cada troca de rota volta para
  // 'carregando' e uma falha derruba só aquela tentativa, não o fluxo que
  // escuta a rota.
  protected readonly estado = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.equipments.buscarPorId(id).pipe(
          switchMap((equipamento) => this.health.indicadoresDoEquipamento(id, equipamento.tag)),
          map((dados) =>
            dados.fisicoQuimico.ensaios.length || dados.gasesDissolvidos.gases.length
              ? ok(dados)
              : vazio,
          ),
          catchError(() => of(erro('A consulta ao AutoLAB falhou. Tente novamente em instantes.'))),
          startWith(carregando),
        ),
      ),
    ),
    { initialValue: carregando as RemoteData<IndicadoresDeSaude> },
  );

  // O @switch estreita `estado().tipo`, mas não estreita `estado().dados` para
  // o template. Este computed faz a ponte sem espalhar casts na view.
  protected readonly dados = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'ok' ? estado.dados : null;
  });

  protected readonly motivoDoErro = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'erro' ? estado.motivo : '';
  });

  protected readonly datasFisicoQuimico = computed(
    () => this.dados()?.fisicoQuimico.coletas.map((coleta) => coleta.data) ?? [],
  );

  protected readonly seriesFisicoQuimico = computed<readonly SerieDoGrafico[]>(() => {
    const bloco = this.dados()?.fisicoQuimico;
    if (!bloco) return [];
    const chaves = Object.keys(NOME_DO_ENSAIO);
    return chaves.map((chave, indice) => ({
      chave,
      nome: NOME_DO_ENSAIO[chave],
      cor: indice,
      valores: bloco.coletas.map((coleta) => coleta.valores[chave] ?? null),
    }));
  });

  protected readonly datasDeGases = computed(
    () => this.dados()?.gasesDissolvidos.coletas.map((coleta) => coleta.data) ?? [],
  );

  protected readonly seriesDeGases = computed<readonly SerieDoGrafico[]>(() => {
    const bloco = this.dados()?.gasesDissolvidos;
    if (!bloco) return [];
    return Object.keys(NOME_DO_GAS).map((chave, indice) => ({
      chave,
      nome: NOME_DO_GAS[chave],
      cor: indice,
      valores: bloco.coletas.map((coleta) => coleta.valores[chave] ?? null),
    }));
  });

  /** Os totais fecham a tabela e não entram no gráfico de evolução. */
  protected readonly gasesMedidos = computed(
    () => this.dados()?.gasesDissolvidos.gases.filter((gas) => !!gas.formula) ?? [],
  );

  protected readonly gasesTotalizadores = computed(
    () => this.dados()?.gasesDissolvidos.gases.filter((gas) => !gas.formula) ?? [],
  );

  protected readonly linhasDeRogers = computed(() => {
    const celulas = this.dados()?.diagnostico.rogers ?? [];
    const linhas = [...new Set(celulas.map((celula) => celula.linha))];
    const colunas = [...new Set(celulas.map((celula) => celula.coluna))];
    return {
      colunas,
      linhas: linhas.map((linha) => ({
        rotulo: linha,
        celulas: colunas.map((coluna) => {
          const encontrada = celulas.find((celula) => celula.linha === linha && celula.coluna === coluna);
          const resultado = encontrada?.resultado ?? 'indeterminado';
          return { coluna, resultado, cor: COR_DE_ROGERS[resultado], rotulo: ROTULO_DE_ROGERS[resultado] };
        }),
      })),
    };
  });

  protected readonly caminhoIeee = computed(() => {
    const ieee = this.dados()?.diagnostico.ieee;
    if (!ieee?.serie.length) return '';
    const maximo = Math.max(...ieee.serie, ieee.alarme) * 1.1;
    const passo = 260 / (ieee.serie.length - 1);
    return ieee.serie
      .map((valor, i) => `${i === 0 ? 'M' : 'L'} ${i * passo} ${110 - (valor / maximo) * 100}`)
      .join(' ');
  });

  protected readonly linhasDeReferenciaIeee = computed(() => {
    const ieee = this.dados()?.diagnostico.ieee;
    if (!ieee) return [];
    const maximo = Math.max(...ieee.serie, ieee.alarme) * 1.1;
    return [
      { rotulo: 'ALARM', y: 110 - (ieee.alarme / maximo) * 100, cor: '#e34948' },
      { rotulo: 'CAUTION', y: 110 - (ieee.atencao / maximo) * 100, cor: '#eda100' },
      { rotulo: 'IN-TOL.', y: 108, cor: '#1baf7a' },
    ];
  });

  protected readonly barrasNbr = computed(() => {
    const leituras = this.dados()?.diagnostico.nbr7274 ?? [];
    if (!leituras.length) return [];
    const maximo = Math.max(...leituras.map((leitura) => Math.max(leitura.valor, leitura.limite))) * 1.1;
    return leituras.map((leitura, i) => ({
      ...leitura,
      x: 24 + i * 46,
      altura: (leitura.valor / maximo) * 96,
      yLimite: 108 - (leitura.limite / maximo) * 96,
    }));
  });

  protected trocarAba(aba: Aba): void {
    this.abaAtiva.set(aba);
  }

  protected trocarSubAba(sub: SubAbaDeGases): void {
    this.subAba.set(sub);
  }
}
