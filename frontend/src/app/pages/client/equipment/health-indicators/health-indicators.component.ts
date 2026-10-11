import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';

import { readableDate } from '@shared/brazilian-date';
import { ClassificationBadgeComponent } from '@shared/components/classification-badge/classification-badge.component';
import { ConformityGaugeComponent } from '@shared/components/conformity-gauge/conformity-gauge.component';
import { DuvalPentagonComponent } from '@shared/components/duval-pentagon/duval-pentagon.component';
import { DuvalTriangleComponent } from '@shared/components/duval-triangle/duval-triangle.component';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import {
  LineChartComponent,
  type ChartSeries,
} from '@shared/components/line-chart/line-chart.component';
import {
  PageHeaderComponent,
  type MigalhaDePao,
} from '@shared/components/page-header/page-header.component';
import { ConformityBannerComponent } from '@shared/components/conformity-banner/conformity-banner.component';

import type {
  DissolvedGas,
  HealthIndicators,
  RogersResult,
  MeasuredValue,
} from '@/app/model/health';
import {
  DEFAULT_HEALTH_TAB,
  isKnownTab,
  type HealthTab,
} from '@/app/model/health-navigation';
import { empty, failure, loading, ok, type RemoteData } from '@/app/model/remote-data';
import { HealthService } from '@/app/services/health.service';

type GasSubTab = 'criterios' | 'metodos';

// Toda cor desta tela chega como classe utilitária inteira, nunca como tom: o
// Tailwind só emite a utilitária que encontra escrita por extenso no fonte, e
// nome montado em tempo de execução não chega ao CSS gerado. Quando a mesma
// cor pinta propriedades diferentes, cada propriedade ganha a sua constante.
const ROGERS_BACKGROUND_CLASS: Record<RogersResult, string> = {
  termico: 'bg-lactec-diagnostico-termico',
  descarga: 'bg-lactec-diagnostico-descarga',
  indeterminado: 'bg-transparent',
};

const ROGERS_LABEL: Record<RogersResult, string> = {
  termico: 'Térmico',
  descarga: 'Descarga',
  indeterminado: 'Sem indicação',
};

// Legenda e marca leem a mesma constante: cor repetida no gabarito faz a
// legenda mentir no dia em que a paleta mudar.
const ROGERS_LEGEND = (['termico', 'descarga'] as const).map((key) => ({
  key,
  label: ROGERS_LABEL[key],
  backgroundClass: ROGERS_BACKGROUND_CLASS[key],
}));

const MEASURED_FILL_CLASS = 'fill-lactec-valor-medido';
const MEASURED_BACKGROUND_CLASS = 'bg-lactec-valor-medido';
const LIMIT_STROKE_CLASS = 'stroke-lactec-limite-da-norma';
const LIMIT_BACKGROUND_CLASS = 'bg-lactec-limite-da-norma';

// Faixas do IEEE C57.104: a severidade é a própria mensagem, então as três
// reusam o trio de feedback do tema em vez de inventar matiz nova.
const IEEE_BAND_CLASSES = {
  alarm: { stroke: 'stroke-lactec-danger', label: 'fill-lactec-danger' },
  attention: { stroke: 'stroke-lactec-warning', label: 'fill-lactec-warning' },
  tolerada: { stroke: 'stroke-lactec-success', label: 'fill-lactec-success' },
} as const;

// Coleta não realizada não é zero nem "não detectado": é lacuna. O laudo
// impresso marca a lacuna com um hífen, e a legenda da tabela explica a marca
// uma vez, em vez de repetir a explicação em cada célula vazia.
const NOT_PERFORMED_MARK = '-';

/** Uma coluna da cabeceira já com a data no formato que o cliente lê. */
interface ReadableColumn {
  readonly readableDate: string;
  readonly isCurrentResult: boolean;
}

function measuredValueText(valor: MeasuredValue): string {
  if (valor === null) return NOT_PERFORMED_MARK;
  if (typeof valor === 'string') return valor;
  return valor.toLocaleString('pt-BR');
}

const NBR_LEGEND = [
  {
    key: 'medido',
    label: 'Medido',
    backgroundClass: MEASURED_BACKGROUND_CLASS,
    format: 'block' as const,
  },
  {
    key: 'limit',
    label: 'Limite',
    backgroundClass: LIMIT_BACKGROUND_CLASS,
    format: 'row' as const,
  },
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
    ConformityBannerComponent,
  ],
  templateUrl: './health-indicators.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HealthIndicatorsComponent {
  readonly id = input.required<string>();
  /** Segmento de rota: qual análise do histórico está na tela. */
  readonly reportId = input<string | undefined>();
  /** Parâmetro de consulta: qual aba abrir. A URL é a única fonte de verdade. */
  readonly aba = input<string | undefined>();

  private readonly health = inject(HealthService);
  private readonly router = inject(Router);
  private readonly currentRoute = inject(ActivatedRoute);

  protected readonly rogersLegend = ROGERS_LEGEND;
  protected readonly nbrLegend = NBR_LEGEND;
  protected readonly measuredFillClass = MEASURED_FILL_CLASS;
  protected readonly limitStrokeClass = LIMIT_STROKE_CLASS;

  // Parâmetro ausente chega como undefined, nunca como o valor anterior, então
  // o padrão vive no derivado e não num signal paralelo que precisasse de
  // sincronia.
  protected readonly activeTab = computed<HealthTab>(() => {
    const requested = this.aba();
    return isKnownTab(requested) ? requested : DEFAULT_HEALTH_TAB;
  });

  protected readonly gasSubTab = signal<GasSubTab>('criterios');

  private readonly query = computed(() => ({
    id: this.id(),
    reportId: this.reportId(),
  }));

  // O router reaproveita a instância entre /equipamentos/1 e /2 e ngOnInit não
  // roda de novo; o switchMap externo cancela a busca anterior. startWith e
  // catchError ficam no pipe INTERNO: assim cada troca de rota volta para
  // 'carregando' e uma falha derruba só aquela tentativa, não o fluxo que
  // escuta a rota.
  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap(({ id, reportId }) =>
        this.health.equipmentIndicators(id, reportId).pipe(
          map((data) =>
            data.physicalChemical.tests.length || data.dissolvedGases.gases.length
              ? ok(data)
              : empty,
          ),
          catchError(() => of(failure('A consulta ao AutoLAB falhou. Tente novamente em instantes.'))),
          startWith(loading),
        ),
      ),
    ),
    { initialValue: loading as RemoteData<HealthIndicators> },
  );

  // O @switch estreita `estado().tipo`, mas não estreita `estado().dados` para
  // o template. Este computed faz a ponte sem espalhar casts na view.
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'ok' ? state.data : null;
  });

  protected readonly breadcrumbs = computed<readonly MigalhaDePao[]>(() => {
    const data = this.data();
    if (!data) return [];
    const detail = `/cliente/equipamentos/${data.equipmentId}`;
    const trail: MigalhaDePao[] = [
      { rotulo: 'Listagem de Equipamentos', rota: '/cliente/equipamentos' },
      { rotulo: data.tag, rota: detail },
      { rotulo: 'Indicadores de Saúde', rota: `${detail}/indicadores` },
    ];
    const analysis = data.selectedAnalysis;
    if (!analysis) return trail;
    return [...trail, { rotulo: `${analysis.label} ${readableDate(analysis.date)}` }];
  });

  protected readonly errorReason = computed(() => {
    const state = this.state();
    return state.kind === 'failure' ? state.reason : '';
  });

  // A API entrega toda data em ISO, que é formato de máquina, e quem sabe o idioma de
  // quem está lendo é o navegador. A conversão acontece aqui, na entrada da tela, e não
  // no template: assim existe um só ponto a mudar se o laudo passar a mostrar a hora.
  protected readonly physicalChemicalDates = computed(
    () => this.data()?.physicalChemical.analyses.map((reading) => readableDate(reading.date)) ?? [],
  );

  // A série sai da lista que o laudo publicou, nunca de uma lista de itens conhecidos escrito aqui.
  // Lista de itens conhecidos paralelo diverge em silêncio: a tela chegou a desenhar legenda de um
  // ensaio que o AutoLAB não mede e a esconder dois que ele mede.
  protected readonly physicalChemicalSeries = computed<readonly ChartSeries[]>(() => {
    const block = this.data()?.physicalChemical;
    if (!block) return [];
    return block.tests.map((test, index) => ({
      key: test.key,
      name: test.name,
      palettePosition: index,
      values: block.analyses.map((reading) => reading.values[test.key] ?? null),
    }));
  });

  protected readonly physicalChemicalColumns = computed<readonly ReadableColumn[]>(
    () =>
      this.data()?.physicalChemical.columns.map((column) => ({
        readableDate: readableDate(column.date),
        isCurrentResult: column.isCurrentResult,
      })) ?? [],
  );

  /**
   * As colunas anteriores à coleta atual. Com uma coleta só esta lista fica
   * vazia e o grupo HISTÓRICO some da tabela inteiro: `colspan="0"` é HTML
   * inválido e um grupo sem coluna não é cabeçalho de coisa alguma.
   */
  protected readonly historyColumns = computed(() =>
    this.physicalChemicalColumns().filter((column) => !column.isCurrentResult),
  );

  protected readonly resultColumn = computed(
    () => this.physicalChemicalColumns().find((column) => column.isCurrentResult) ?? null,
  );

  /**
   * Casa cada valor com a sua coluna pela posição, e é a posição que manda: a
   * linha percorre as colunas, não a própria lista. Uma linha mais curta do
   * que as colunas vira lacuna no fim, e não um deslocamento que faria a
   * tabela mostrar o valor de 2021 debaixo da data de 2025.
   */
  protected readonly physicalChemicalRows = computed(() => {
    const columns = this.physicalChemicalColumns();
    const resultIndex = columns.findIndex((column) => column.isCurrentResult);
    return (this.data()?.physicalChemical.tests ?? []).map((test) => ({
      key: test.key,
      name: test.name,
      method: test.method,
      limit: test.limit,
      classification: test.classification,
      history: columns
        .map((column, index) => ({ column, value: test.valuesByAnalysis[index] ?? null }))
        .filter((cell) => !cell.column.isCurrentResult)
        .map((cell) => measuredValueText(cell.value)),
      result: measuredValueText(test.valuesByAnalysis[resultIndex] ?? null),
    }));
  });

  protected readonly gasDates = computed(
    () => this.data()?.dissolvedGases.analyses.map((reading) => readableDate(reading.date)) ?? [],
  );

  protected readonly gasColumnDates = computed(
    () => this.data()?.dissolvedGases.analysisDates.map(readableDate) ?? [],
  );

  /** A fórmula distingue gás medido de totalizador, e o totalizador não evolui. */
  protected readonly gasSeries = computed<readonly ChartSeries[]>(() => {
    const block = this.data()?.dissolvedGases;
    if (!block) return [];
    return block.gases
      .filter((gas) => !!gas.formula)
      .map((gas, index) => ({
        key: gas.key,
        name: gas.formula,
        palettePosition: index,
        values: block.analyses.map((reading) => reading.values[gas.key] ?? null),
      }));
  });

  /** Os totais fecham a tabela e não entram no gráfico de evolução. */
  protected readonly measuredGases = computed(() => this.formattedGases((gas) => !!gas.formula));

  protected readonly totalizerGases = computed(() =>
    this.formattedGases((gas) => !gas.formula),
  );

  private formattedGases(choose: (gas: DissolvedGas) => boolean) {
    return (this.data()?.dissolvedGases.gases ?? []).filter(choose).map((gas) => ({
      key: gas.key,
      name: gas.name,
      formula: gas.formula,
      results: gas.results.map(measuredValueText),
    }));
  }

  /**
   * O bloco de diagnóstico chega nulo enquanto o núcleo normativo não roda no backend, e
   * continua nulo em laudo sem gás dissolvido. Um sinal só resolve a ausência, e todo o
   * resto da tela lê daqui em vez de repetir a checagem e esquecer dela em um dos pontos.
   */
  protected readonly diagnosis = computed(() => this.data()?.diagnosis ?? null);

  protected readonly rogersRows = computed(() => {
    const cells = this.diagnosis()?.rogers ?? [];
    const rows = [...new Set(cells.map((cell) => cell.row))];
    const columns = [...new Set(cells.map((cell) => cell.column))];
    return {
      columns,
      rows: rows.map((row) => ({
        label: row,
        cells: columns.map((column) => {
          const found = cells.find((cell) => cell.row === row && cell.column === column);
          const result = found?.result ?? 'indeterminado';
          return {
            column,
            result,
            backgroundClass: ROGERS_BACKGROUND_CLASS[result],
            label: ROGERS_LABEL[result],
          };
        }),
      })),
    };
  });

  protected readonly ieeePath = computed(() => {
    const ieee = this.diagnosis()?.ieee;
    if (!ieee?.series.length) return '';
    const maximum = Math.max(...ieee.series, ieee.alarm) * 1.1;
    const firstPointHeight = 110 - (ieee.series[0] / maximum) * 100;
    if (ieee.series.length < 2) return `M 0 ${firstPointHeight}`;
    const step = 260 / (ieee.series.length - 1);
    return ieee.series
      .map((valor, i) => `${i === 0 ? 'M' : 'L'} ${i * step} ${110 - (valor / maximum) * 100}`)
      .join(' ');
  });

  protected readonly ieeeReferenceLines = computed(() => {
    const ieee = this.diagnosis()?.ieee;
    if (!ieee) return [];
    const maximum = Math.max(...ieee.series, ieee.alarm) * 1.1;
    return [
      {
        label: 'ALARM',
        y: 110 - (ieee.alarm / maximum) * 100,
        classes: IEEE_BAND_CLASSES.alarm,
      },
      {
        label: 'CAUTION',
        y: 110 - (ieee.attention / maximum) * 100,
        classes: IEEE_BAND_CLASSES.attention,
      },
      { label: 'IN-TOL.', y: 108, classes: IEEE_BAND_CLASSES.tolerada },
    ];
  });

  protected readonly nbrBars = computed(() => {
    const readings = this.diagnosis()?.nbr7274 ?? [];
    if (!readings.length) return [];
    const maximum = Math.max(...readings.map((reading) => Math.max(reading.value, reading.limit))) * 1.1;
    return readings.map((reading, i) => ({
      ...reading,
      x: 24 + i * 46,
      height: (reading.value / maximum) * 96,
      limitY: 108 - (reading.limit / maximum) * 96,
    }));
  });

  // Trocar de aba troca a URL, para o link da linha do histórico poder apontar
  // direto para a aba certa. `replaceUrl` evita que cada clique de aba empilhe
  // uma entrada no botão Voltar.
  protected selectTab(aba: HealthTab): void {
    void this.router.navigate([], {
      relativeTo: this.currentRoute,
      queryParams: { aba },
      replaceUrl: true,
    });
  }

  protected selectGasSubTab(sub: GasSubTab): void {
    this.gasSubTab.set(sub);
  }
}
