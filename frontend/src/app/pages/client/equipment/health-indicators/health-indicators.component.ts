import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';

import { readableDate } from '@shared/brazilian-date';
import { ClassificationBadgeComponent } from '@shared/components/classification-badge/classification-badge.component';
import { ConformityGaugeComponent } from '@shared/components/conformity-gauge/conformity-gauge.component';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import {
  LineChartComponent,
  type ChartSeries,
} from '@shared/components/line-chart/line-chart.component';
import {
  PageHeaderComponent,
  type Breadcrumb,
} from '@shared/components/page-header/page-header.component';
import { ConformityBannerComponent } from '@shared/components/conformity-banner/conformity-banner.component';

import {
  NOT_ASSESSED_LABEL,
  type GasRatioReading,
  type DissolvedGas,
  type HealthIndicators,
  type MeasuredValue,
  type RatioMethodReading,
} from '@/app/model/health';
import {
  DEFAULT_HEALTH_TAB,
  isKnownTab,
  type HealthTab,
} from '@/app/model/health-navigation';
import { empty, failure, loading, ok, type RemoteData } from '@/app/model/remote-data';
import { DissolvedGasDiagnosisService } from '@/app/services/dissolved-gas-diagnosis.service';
import { HealthService } from '@/app/services/health.service';

type GasSubTab = 'criterios' | 'metodos';

// Toda cor desta tela chega como classe utilitária inteira, nunca como tom: o
// Tailwind só emite a utilitária que encontra escrita por extenso no fonte, e
// nome montado em tempo de execução não chega ao CSS gerado. Quando a mesma
// cor pinta propriedades diferentes, cada propriedade ganha a sua constante.
const MEASURED_FILL_CLASS = 'fill-lactec-valor-medido';
const MEASURED_BACKGROUND_CLASS = 'bg-lactec-valor-medido';
const LIMIT_STROKE_CLASS = 'stroke-lactec-limite-da-norma';
const LIMIT_BACKGROUND_CLASS = 'bg-lactec-limite-da-norma';

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

import { DiagnosticMethodsComponent } from './diagnostic-methods/diagnostic-methods.component';

@Component({
  selector: 'app-indicadores',
  standalone: true,
  imports: [
    DiagnosticMethodsComponent,
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ConformityGaugeComponent,
    ClassificationBadgeComponent,
    LineChartComponent,
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
  private readonly diagnosisCalculation = inject(DissolvedGasDiagnosisService);
  private readonly router = inject(Router);
  private readonly currentRoute = inject(ActivatedRoute);

  protected readonly notAssessedLabel = NOT_ASSESSED_LABEL;

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
          catchError(() => of(failure('A consulta ao AutoLAB loadFailed. Tente novamente em instantes.'))),
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

  protected readonly breadcrumbs = computed<readonly Breadcrumb[]>(() => {
    const data = this.data();
    if (!data) return [];
    const detail = `/cliente/equipamentos/${data.equipmentId}`;
    const trail: Breadcrumb[] = [
      { label: 'Listagem de Equipamentos', route: '/cliente/equipamentos' },
      { label: data.tag, route: detail },
      { label: 'Indicadores de Saúde', route: `${detail}/indicadores` },
    ];
    const analysis = data.selectedAnalysis;
    if (!analysis) return trail;
    return [...trail, { label: `${analysis.label} ${readableDate(analysis.date)}` }];
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
   * O bloco de diagnóstico ainda chega nulo do backend, e quem calcula os sete
   * métodos é o serviço, a partir dos gases dissolvidos que já viajam no mesmo
   * corpo. O `??` mantém a ordem certa de precedência: no dia em que a API
   * servir o bloco, o valor servido vence e esta tela não muda. Em laudo sem gás
   * dissolvido o serviço devolve nulo, e aí não há método a mostrar.
   */
  protected readonly diagnosis = computed(() => {
    const data = this.data();
    if (!data) return null;
    return data.diagnosis ?? this.diagnosisCalculation.diagnose(data.dissolvedGases);
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
