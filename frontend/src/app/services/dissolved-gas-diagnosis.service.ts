import { Injectable } from '@angular/core';

import {
  classificarDuval1,
  duval1,
  gasAusenteDoDuval1,
  zonasDuval1Com,
  DESCRICAO_DE_DUVAL_1,
  type CodigoDeDuval1,
} from '@/app/model/duval-1';
import {
  aplicaSeDuval4,
  classificarDuval4,
  duval4,
  gasAusenteDoDuval4,
  zonasDuval4Com,
  DESCRICAO_DE_DUVAL_4,
  MOTIVO_DUVAL_4_NAO_APLICAVEL,
  type CodigoDeDuval4,
} from '@/app/model/duval-4';
import {
  aplicaSeDuval5,
  classificarDuval5,
  duval5,
  gasAusenteDoDuval5,
  zonasDuval5Com,
  DESCRICAO_DE_DUVAL_5,
  MOTIVO_DUVAL_5_NAO_APLICAVEL,
  type CodigoDeDuval5,
} from '@/app/model/duval-5';
import {
  missingPentagonGas,
  pentagonCentroid,
  pentagonPercentages,
  PENTAGON_ZONE_NOT_ASSESSED,
  type PentagonGases,
} from '@/app/model/duval-pentagon-1';
import { gasRatio, type GasRatioDefinition } from '@/app/model/gas-ratio';
import {
  IEC_60599_FAULT_CODES,
  IEC_60599_FAULT_CODE_NOT_ASSESSED,
  IEC_60599_FAULT_GASES,
  IEC_60599_RATIOS,
  NBR_7274_LIMITS_NOT_ASSESSED,
} from '@/app/model/iec-60599';
import {
  combustibleGasWithoutTest,
  totalCombustibleGases,
  IEEE_THRESHOLDS_NOT_ASSESSED,
  type CombustibleGases,
} from '@/app/model/ieee-c57-104';
import { ROGERS_FAULT_CODE_NOT_ASSESSED, ROGERS_RATIOS } from '@/app/model/rogers';
import {
  NOT_ASSESSED_LABEL,
  NOT_DETECTED_TEXT,
  type CombustibleGasReading,
  type DiagnosisBlock,
  type DiagnosisConclusion,
  type DissolvedGasBlock,
  type DuvalTriangle,
  type GasReadingNBR7274,
  type MeasuredValue,
  type Nbr7274Block,
  type PentagonReading,
  type RatioMethodReading,
  type TernaryPoint,
  type TriangleReading,
} from '@/app/model/health';

/** Uma coleta vista pelo diagnóstico: a data e a concentração de cada gás, em ppm. */
interface GasReading {
  readonly date: string;
  readonly concentrations: Readonly<Record<string, number | null>>;
}

// Os rótulos de eixo são os das telas aprovadas e precisam continuar batendo com
// a convenção de eixos de cada módulo de triângulo: `a` é o vértice de cima, `b`
// é a direita e `c` é a base. Trocar dois rótulos de lugar é o defeito que nem o
// compilador nem a suíte enxergam, porque os três são texto do mesmo tipo.
const DUVAL_1_AXES = {
  leftAxis: 'Methane CH₄ %',
  rightAxis: 'Ethylene C₂H₄ %',
  baseAxis: 'Acetylene C₂H₂ %',
} as const;

const DUVAL_4_AXES = {
  leftAxis: 'Hydrogen H₂ %',
  rightAxis: 'Methane CH₄ %',
  baseAxis: 'Ethane C₂H₆ %',
} as const;

const DUVAL_5_AXES = {
  leftAxis: 'Methane CH₄ %',
  rightAxis: 'Ethylene C₂H₄ %',
  baseAxis: 'Ethane C₂H₆ %',
} as const;

const TRIANGLE_1_WITHOUT_READING = 'Nenhuma coleta deste laudo tem os três gases do triângulo 1';
const TRIANGLE_4_WITHOUT_READING = 'Nenhuma coleta deste laudo tem os três gases do triângulo 4';
const TRIANGLE_5_WITHOUT_READING = 'Nenhuma coleta deste laudo tem os três gases do triângulo 5';
const PENTAGON_WITHOUT_DETECTED_GAS = 'Nenhum dos cinco gases do pentágono foi detectado nesta coleta';
const PENTAGON_WITH_A_SINGLE_GAS =
  'Só um dos cinco gases foi detectado: o pentágono não tem área e o centroide não existe';

/**
 * O que a célula do laudo oferece ao cálculo.
 *
 * `'ND'` é ensaio feito sem detecção e entra como zero, porque é medição: é
 * assim que o laudo com os três gases do triângulo em `'ND'` cai no caso real de
 * total zero, em que o triângulo é incomputável por não haver gás, e não por não
 * haver ensaio. Célula vazia é ensaio não realizado e continua ausente. Texto
 * que não é número nem `'ND'` não carrega concentração nenhuma, então também
 * fica ausente, e é a lista de resultados ilegíveis do corpo que diz de qual
 * laudo aquela célula veio.
 *
 * `Number.isFinite` recusa `NaN` e infinito. JSON não transporta nenhum dos dois,
 * então a guarda não protege contra o fio: protege contra massa de teste e contra
 * conta feita antes daqui, e um `NaN` que entrasse viraria ponto fantasma no
 * triângulo sem acusar erro nenhum.
 */
function concentrationOf(value: MeasuredValue): number | null {
  if (value === NOT_DETECTED_TEXT) return 0;
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  return null;
}

/**
 * Calcula os métodos de diagnóstico a partir dos gases dissolvidos que o laudo
 * publica.
 *
 * Por que um serviço injetável, e não uma cadeia de `computed` no componente:
 * o conhecimento normativo é de domínio, não de tela, e este é o ponto único em
 * que o formato do fio encontra os módulos de norma. Enquanto o núcleo normativo
 * não roda no backend, este serviço é o núcleo; quando rodar, a API passa a
 * servir `diagnosis` preenchido, o componente deixa de chamar daqui e nenhuma
 * tela muda de forma. É também o que permite a massa de demonstração e a tela
 * lerem o mesmo caminho de código: a derivação dos triângulos morava na massa de
 * teste, e massa com derivação própria diverge da tela sem ninguém notar.
 */
@Injectable({ providedIn: 'root' })
export class DissolvedGasDiagnosisService {
  /**
   * Devolve `null` quando o laudo não tem gás dissolvido nenhum, que é o caso
   * de laudo de área que não é Óleos: aí não há o que diagnosticar, e a tela
   * diz isso em vez de desenhar sete painéis vazios.
   */
  diagnose(block: DissolvedGasBlock): DiagnosisBlock | null {
    const readings = this.gasReadings(block);
    const currentReading = readings.at(-1);
    if (!currentReading) return null;

    const triangle1Readings = this.triangle1Readings(readings);
    const codesByReading = triangle1Readings.map((reading) =>
      reading.kind === 'plotada' ? classificarDuval1(reading.point) : null,
    );
    const triangle4Readings = this.conditionalReadings({
      readings,
      codesByReading,
      gasesOf: (reading) => ({
        h2: reading.concentrations['h2'] ?? null,
        ch4: reading.concentrations['ch4'] ?? null,
        c2h6: reading.concentrations['c2h6'] ?? null,
      }),
      appliesTo: aplicaSeDuval4,
      reason: MOTIVO_DUVAL_4_NAO_APLICAVEL,
      toPoint: duval4,
      missingGas: gasAusenteDoDuval4,
    });
    const triangle5Readings = this.conditionalReadings({
      readings,
      codesByReading,
      gasesOf: (reading) => ({
        ch4: reading.concentrations['ch4'] ?? null,
        c2h4: reading.concentrations['c2h4'] ?? null,
        c2h6: reading.concentrations['c2h6'] ?? null,
      }),
      appliesTo: aplicaSeDuval5,
      reason: MOTIVO_DUVAL_5_NAO_APLICAVEL,
      toPoint: duval5,
      missingGas: gasAusenteDoDuval5,
    });

    const codes1 = codesByReading.filter((code): code is CodigoDeDuval1 => code !== null);
    const codes4 = this.plottedCodes(triangle4Readings, classificarDuval4);
    const codes5 = this.plottedCodes(triangle5Readings, classificarDuval5);

    const triangles: readonly DuvalTriangle[] = [
      {
        number: 1,
        ...DUVAL_1_AXES,
        zones: zonasDuval1Com(codes1),
        readings: triangle1Readings,
        notApplicable: null,
      },
      {
        number: 4,
        ...DUVAL_4_AXES,
        zones: zonasDuval4Com(codes4),
        readings: triangle4Readings,
        notApplicable: this.emptyFigureReason(triangle4Readings, MOTIVO_DUVAL_4_NAO_APLICAVEL),
      },
      {
        number: 5,
        ...DUVAL_5_AXES,
        zones: zonasDuval5Com(codes5),
        readings: triangle5Readings,
        notApplicable: this.emptyFigureReason(triangle5Readings, MOTIVO_DUVAL_5_NAO_APLICAVEL),
      },
    ];

    const pentagon = this.pentagonReading(currentReading);

    return {
      triangles,
      pentagon,
      ieee: {
        readings: this.combustibleGasReadings(readings),
        alarm: null,
        attention: null,
        thresholdsNotAssessed: IEEE_THRESHOLDS_NOT_ASSESSED,
      },
      iecCodes: IEC_60599_FAULT_CODES,
      iec60599: this.ratioMethodReading(
        currentReading,
        IEC_60599_RATIOS,
        IEC_60599_FAULT_CODE_NOT_ASSESSED,
      ),
      nbr7274: this.nbr7274Block(currentReading),
      rogers: this.ratioMethodReading(
        currentReading,
        ROGERS_RATIOS,
        ROGERS_FAULT_CODE_NOT_ASSESSED,
      ),
      conclusions: [
        this.conclusionOf({
          method: 'Triângulo 1',
          codes: codes1,
          descriptions: DESCRICAO_DE_DUVAL_1,
          withoutVerdict: TRIANGLE_1_WITHOUT_READING,
        }),
        this.conclusionOf({
          method: 'Triângulo 4',
          codes: codes4,
          descriptions: DESCRICAO_DE_DUVAL_4,
          withoutVerdict:
            this.emptyFigureReason(triangle4Readings, MOTIVO_DUVAL_4_NAO_APLICAVEL) ??
            TRIANGLE_4_WITHOUT_READING,
        }),
        this.conclusionOf({
          method: 'Triângulo 5',
          codes: codes5,
          descriptions: DESCRICAO_DE_DUVAL_5,
          withoutVerdict:
            this.emptyFigureReason(triangle5Readings, MOTIVO_DUVAL_5_NAO_APLICAVEL) ??
            TRIANGLE_5_WITHOUT_READING,
        }),
        {
          method: 'Pentágono de Duval',
          code: NOT_ASSESSED_LABEL,
          description: PENTAGON_ZONE_NOT_ASSESSED,
        },
        {
          method: 'NBR 7274 / IEC 60599',
          code: NOT_ASSESSED_LABEL,
          description: IEC_60599_FAULT_CODE_NOT_ASSESSED,
        },
        {
          method: 'Rogers',
          code: NOT_ASSESSED_LABEL,
          description: ROGERS_FAULT_CODE_NOT_ASSESSED,
        },
        {
          method: 'IEEE C57.104',
          code: NOT_ASSESSED_LABEL,
          description: IEEE_THRESHOLDS_NOT_ASSESSED,
        },
      ],
    };
  }

  /**
   * Percorre as datas das colunas, nunca a lista de gases, e é isso que mantém
   * cada concentração debaixo da sua data: gás com menos resultados que colunas
   * abre lacuna no fim, em vez de deslocar a série inteira. Totalizador fica
   * fora do cálculo, e quem o identifica no fio é a fórmula vazia.
   */
  private gasReadings(block: DissolvedGasBlock): readonly GasReading[] {
    const measuredGases = block.gases.filter((gas) => gas.formula !== '');
    if (!measuredGases.length) return [];

    return block.analysisDates.map((date, index) => ({
      date,
      concentrations: Object.fromEntries(
        measuredGases.map((gas) => [gas.key, concentrationOf(gas.results[index] ?? null)]),
      ),
    }));
  }

  private triangle1Readings(readings: readonly GasReading[]): readonly TriangleReading[] {
    return readings.map((reading) => {
      const gases = {
        ch4: reading.concentrations['ch4'] ?? null,
        c2h4: reading.concentrations['c2h4'] ?? null,
        c2h2: reading.concentrations['c2h2'] ?? null,
      };
      const point = duval1(gases);

      if (point) return { kind: 'plotada', date: reading.date, point } as const;

      const missingGas = gasAusenteDoDuval1(gases);

      return missingGas === null
        ? ({ kind: 'semProporcao', date: reading.date } as const)
        : ({ kind: 'incomputavel', date: reading.date, missingGas } as const);
    });
  }

  /**
   * Leituras de um triângulo condicional. Quatro desfechos, e os quatro continuam
   * na lista: plotada; incomputável porque faltou gás; sem proporção porque os três
   * gases vieram não detectados, que é zero sobre zero e não é ponto; e não
   * aplicável porque o triângulo 1 não licenciou a figura para aquela coleta. A
   * licença é por falha diagnosticada, coleta a coleta, e não pelo lote.
   *
   * Parâmetros em objeto de propósito: o defeito de origem desta tela foi troca
   * de eixo invisível ao compilador, e função com vários posicionais do mesmo
   * tipo é exatamente como isso volta.
   */
  private conditionalReadings<G>(options: {
    readonly readings: readonly GasReading[];
    readonly codesByReading: readonly (CodigoDeDuval1 | null)[];
    readonly gasesOf: (reading: GasReading) => G;
    readonly appliesTo: (code: CodigoDeDuval1 | null) => boolean;
    readonly reason: string;
    readonly toPoint: (gases: G) => TernaryPoint | null;
    readonly missingGas: (gases: G) => string | null;
  }): readonly TriangleReading[] {
    return options.readings.map((reading, index) => {
      if (!options.appliesTo(options.codesByReading[index])) {
        return { kind: 'naoAplicavel', date: reading.date, reason: options.reason } as const;
      }
      const gases = options.gasesOf(reading);
      const point = options.toPoint(gases);

      if (point) return { kind: 'plotada', date: reading.date, point } as const;

      const missingGas = options.missingGas(gases);

      return missingGas === null
        ? ({ kind: 'semProporcao', date: reading.date } as const)
        : ({ kind: 'incomputavel', date: reading.date, missingGas } as const);
    });
  }

  private plottedCodes<C>(
    readings: readonly TriangleReading[],
    classify: (point: TernaryPoint) => C,
  ): readonly C[] {
    return readings
      .filter((reading) => reading.kind === 'plotada')
      .map((reading) => classify(reading.point));
  }

  /**
   * Motivo a exibir quando NENHUMA coleta licenciou a figura. Se faltou gás, o
   * motivo não é esse: quem explica é a contagem de incomputáveis.
   */
  private emptyFigureReason(
    readings: readonly TriangleReading[],
    reason: string,
  ): string | null {
    if (readings.some((reading) => reading.kind === 'plotada')) return null;
    return readings.some((reading) => reading.kind === 'naoAplicavel') ? reason : null;
  }

  private pentagonReading(reading: GasReading): PentagonReading {
    const gases: PentagonGases = {
      h2: reading.concentrations['h2'] ?? null,
      c2h6: reading.concentrations['c2h6'] ?? null,
      ch4: reading.concentrations['ch4'] ?? null,
      c2h4: reading.concentrations['c2h4'] ?? null,
      c2h2: reading.concentrations['c2h2'] ?? null,
    };
    const point = pentagonCentroid(gases);
    const percentages = pentagonPercentages(gases);

    return {
      date: reading.date,
      point,
      notPlotted: point ? null : this.reasonWithoutPentagonPoint(gases, percentages),
      zone: null,
      zoneNotAssessed: PENTAGON_ZONE_NOT_ASSESSED,
      percentages,
    };
  }

  private reasonWithoutPentagonPoint(
    gases: PentagonGases,
    percentages: readonly { readonly percentage: number | null }[],
  ): string {
    const missing = missingPentagonGas(gases);
    if (missing) return `Sem ensaio de ${missing}`;
    if (percentages.every((item) => item.percentage === null)) return PENTAGON_WITHOUT_DETECTED_GAS;
    return PENTAGON_WITH_A_SINGLE_GAS;
  }

  private combustibleGasReadings(
    readings: readonly GasReading[],
  ): readonly CombustibleGasReading[] {
    return readings.map((reading) => {
      const gases: CombustibleGases = {
        h2: reading.concentrations['h2'] ?? null,
        ch4: reading.concentrations['ch4'] ?? null,
        c2h6: reading.concentrations['c2h6'] ?? null,
        c2h4: reading.concentrations['c2h4'] ?? null,
        c2h2: reading.concentrations['c2h2'] ?? null,
        co: reading.concentrations['co'] ?? null,
      };
      const total = totalCombustibleGases(gases);
      const withoutTest = combustibleGasWithoutTest(gases);
      return {
        date: reading.date,
        total,
        notComputed: total === null ? `Sem ensaio de ${withoutTest}` : null,
      };
    });
  }

  private ratioMethodReading(
    reading: GasReading,
    definitions: readonly GasRatioDefinition[],
    faultCodeNotAssessed: string,
  ): RatioMethodReading {
    return {
      date: reading.date,
      ratios: definitions.map((definition) => gasRatio(definition, reading.concentrations)),
      faultCode: null,
      faultCodeNotAssessed,
    };
  }

  private nbr7274Block(reading: GasReading): Nbr7274Block {
    const readings: readonly GasReadingNBR7274[] = IEC_60599_FAULT_GASES.map((gas) => ({
      gas: gas.formula,
      value: reading.concentrations[gas.key] ?? null,
      limit: null,
    }));

    return { date: reading.date, readings, limitsNotAssessed: NBR_7274_LIMITS_NOT_ASSESSED };
  }

  /**
   * O veredito de um método é o que a tabela normativa diz sobre a coleta mais
   * recente que ele pôde ler. Sem leitura não sai código: emitir código sobre
   * figura não licenciada é o erro que esta tela cometia ao desenhar os três
   * triângulos para toda amostra.
   */
  private conclusionOf<C extends string>(options: {
    readonly method: string;
    readonly codes: readonly C[];
    readonly descriptions: Record<C, string>;
    readonly withoutVerdict: string;
  }): DiagnosisConclusion {
    const latest = options.codes.at(-1);
    return latest
      ? { method: options.method, code: latest, description: options.descriptions[latest] }
      : { method: options.method, code: NOT_ASSESSED_LABEL, description: options.withoutVerdict };
  }
}
