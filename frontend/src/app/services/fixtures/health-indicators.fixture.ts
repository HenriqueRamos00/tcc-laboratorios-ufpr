// Resultados, datas e traçado dos gráficos vêm das telas aprovadas.
//
// As zonas dos três triângulos de Duval saíram daqui: agora são derivadas das
// tabelas normativas em `model/duval-1.ts`, `duval-4.ts` e `duval-5.ts`. As
// dos triângulos 4 e 5 eram desenhadas "pelo layout da tela" e davam
// diagnóstico diferente do publicado em 68,0% e 67,5% da área - ver a
// proveniência no topo de cada módulo.
//
// Os triângulos 4 e 5 também passaram a respeitar a regra de aplicabilidade:
// são refinamentos condicionais do triângulo 1, não uma segunda opinião.

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
import type {
  FaultCode,
  AnalysisReading,
  AnalysisColumn,
  DiagnosisConclusion,
  HealthIndicators,
  TriangleReading,
  TernaryPoint,
  MeasuredValue,
} from '@/app/model/health';

// A API entrega toda data em ISO; a tela formata na entrada. Fixture com data já
// formatada esconderia exatamente o passo que a tela precisa dar.
const DATAS_FQ = ['2021-04-20', '2022-05-20', '2023-09-19', '2024-04-26', '2025-10-26'];


// --- Triângulo 1 derivado das coletas ---------------------------------------
//
// Os pontos NÃO são chumbados: saem dos mesmos ppm que alimentam a tabela de
// gases, senão o desenho e a tabela divergem sem ninguém notar. Três das cinco
// coletas não têm C₂H₂ reportado e por isso não viram ponto - elas continuam
// na lista, como `incomputavel`, para a tela poder dizer isso em voz alta.
const COLETAS_DE_GASES: readonly AnalysisReading[] = [
      {
        date: DATAS_FQ[0],
        values: {
          h2: 4300, o2: 5100, n2: 55000, ch4: 110, co: 270, co2: 4500, c2h4: 2400, c2h6: 14, c2h2: null,
        },
      },
      {
        date: DATAS_FQ[1],
        values: {
          h2: 590, o2: 2400, n2: 68000, ch4: 68, co: 250, co2: 3500, c2h4: 150, c2h6: 10, c2h2: null,
        },
      },
      {
        date: DATAS_FQ[2],
        values: {
          h2: 1030, o2: 3100, n2: 53000, ch4: 250, co: 280, co2: 6000, c2h4: 280, c2h6: 690, c2h2: null,
        },
      },
      {
        date: DATAS_FQ[3],
        values: {
          h2: 18500, o2: 5000, n2: 59000, ch4: 250, co: 290, co2: 5100, c2h4: 380, c2h6: 680, c2h2: 2,
        },
      },
      {
        date: DATAS_FQ[4],
        values: {
          h2: 44000, o2: 4300, n2: 47000, ch4: 3600, co: 330, co2: 6200, c2h4: 3800, c2h6: 2400, c2h2: 26,
        },
      },
];

function leiturasDoDuval1(analyses: readonly AnalysisReading[]): readonly TriangleReading[] {
  return analyses.map((coleta) => {
    const gases = {
      ch4: coleta.values['ch4'] ?? null,
      c2h4: coleta.values['c2h4'] ?? null,
      c2h2: coleta.values['c2h2'] ?? null,
    };
    const point = duval1(gases);
    return point
      ? ({ kind: 'plotada', date: coleta.date, point } as const)
      : ({
          kind: 'incomputavel',
          date: coleta.date,
          missingGas: gasAusenteDoDuval1(gases) ?? 'gás',
        } as const);
  });
}

const LEITURAS_DUVAL_1 = leiturasDoDuval1(COLETAS_DE_GASES);

// Um código por coleta, alinhado com COLETAS_DE_GASES; `null` quando a coleta
// não deu para computar. É esta lista que licencia os triângulos 4 e 5 -
// coleta a coleta, porque a licença é por falha diagnosticada, não pelo lote.
const CODIGO_DUVAL_1_POR_COLETA: readonly (CodigoDeDuval1 | null)[] = LEITURAS_DUVAL_1.map(
  (leitura) => (leitura.kind === 'plotada' ? classificarDuval1(leitura.point) : null),
);

const CODIGOS_DUVAL_1: readonly CodigoDeDuval1[] = CODIGO_DUVAL_1_POR_COLETA.filter(
  (code): code is CodigoDeDuval1 => code !== null,
);

// --- Triângulos 4 e 5, condicionados ao triângulo 1 -------------------------
//
// IEEE Std C57.104-2019, p. 66: o triângulo 4 só vale depois de PD, T1 ou T2;
// o 5, depois de T2 ou T3; nenhum dos dois vale para D1 ou D2. Como a
// geometria cobre o simplex inteiro, os dois SEMPRE devolvem uma zona - é por
// isso que o portão precisa estar no código, e não no bom senso de quem lê.

/**
 * Deriva as leituras de um triângulo condicional das mesmas coletas que
 * alimentam a tabela de gases. Três desfechos, e os três continuam na lista:
 * plotada, incomputável (faltou gás) e não aplicável (sobra gás, falta
 * licença do triângulo 1).
 *
 * Parâmetros em objeto de propósito: o defeito de origem deste projeto foi
 * troca de eixo invisível ao compilador, e função com vários posicionais do
 * mesmo tipo é exatamente como isso volta.
 */
function leiturasCondicionais<G>(opcoes: {
  readonly analyses: readonly AnalysisReading[];
  readonly gasesDa: (coleta: AnalysisReading) => G;
  readonly aplicaSe: (code: CodigoDeDuval1 | null) => boolean;
  readonly reason: string;
  readonly paraPonto: (gases: G) => TernaryPoint | null;
  readonly missingGas: (gases: G) => string | null;
}): readonly TriangleReading[] {
  const { analyses, gasesDa, aplicaSe, reason, paraPonto, missingGas } = opcoes;
  return analyses.map((coleta, indice) => {
    if (!aplicaSe(CODIGO_DUVAL_1_POR_COLETA[indice])) {
      return { kind: 'naoAplicavel', date: coleta.date, reason } as const;
    }
    const gases = gasesDa(coleta);
    const point = paraPonto(gases);
    return point
      ? ({ kind: 'plotada', date: coleta.date, point } as const)
      : ({
          kind: 'incomputavel',
          date: coleta.date,
          missingGas: missingGas(gases) ?? 'gás',
        } as const);
  });
}

const LEITURAS_DUVAL_4 = leiturasCondicionais({
  analyses: COLETAS_DE_GASES,
  gasesDa: (coleta) => ({
    h2: coleta.values['h2'] ?? null,
    ch4: coleta.values['ch4'] ?? null,
    c2h6: coleta.values['c2h6'] ?? null,
  }),
  aplicaSe: aplicaSeDuval4,
  reason: MOTIVO_DUVAL_4_NAO_APLICAVEL,
  paraPonto: duval4,
  missingGas: gasAusenteDoDuval4,
});

const LEITURAS_DUVAL_5 = leiturasCondicionais({
  analyses: COLETAS_DE_GASES,
  gasesDa: (coleta) => ({
    ch4: coleta.values['ch4'] ?? null,
    c2h4: coleta.values['c2h4'] ?? null,
    c2h6: coleta.values['c2h6'] ?? null,
  }),
  aplicaSe: aplicaSeDuval5,
  reason: MOTIVO_DUVAL_5_NAO_APLICAVEL,
  paraPonto: duval5,
  missingGas: gasAusenteDoDuval5,
});

const CODIGOS_DUVAL_4: readonly CodigoDeDuval4[] = LEITURAS_DUVAL_4.filter(
  (leitura) => leitura.kind === 'plotada',
).map((leitura) => classificarDuval4(leitura.point));

const CODIGOS_DUVAL_5: readonly CodigoDeDuval5[] = LEITURAS_DUVAL_5.filter(
  (leitura) => leitura.kind === 'plotada',
).map((leitura) => classificarDuval5(leitura.point));

/**
 * Motivo a exibir quando NENHUMA coleta licenciou a figura. Se faltou gás, o
 * motivo não é esse - quem explica é a contagem de incomputáveis.
 */
function motivoDaFiguraVazia(
  readings: readonly TriangleReading[],
  reason: string,
): string | null {
  if (readings.some((leitura) => leitura.kind === 'plotada')) return null;
  return readings.some((leitura) => leitura.kind === 'naoAplicavel') ? reason : null;
}

// DT fica de fora: é zona do triângulo de Duval (falha mista térmica/elétrica),
// não código de falha da IEC 60599, que só define PD, D1, D2, T1, T2 e T3.
// Listá-lo aqui faria a legenda atribuir à norma um código que ela não tem.
const CODIGOS_IEC: readonly FaultCode[] = (
  Object.keys(DESCRICAO_DE_DUVAL_1) as CodigoDeDuval1[]
)
  .filter((code) => code !== 'DT')
  .map((code) => ({ code, description: DESCRICAO_DE_DUVAL_1[code] }));

// A conclusão do triângulo 1 é o que a tabela normativa diz sobre a coleta mais
// recente que deu para computar, não um texto fixo.
const ULTIMO_CODIGO_DUVAL_1 = CODIGOS_DUVAL_1.at(-1);

const CONCLUSOES_DUVAL_1: readonly DiagnosisConclusion[] = [
  ULTIMO_CODIGO_DUVAL_1
    ? {
        method: 'Triangulo 1',
        code: ULTIMO_CODIGO_DUVAL_1,
        description: DESCRICAO_DE_DUVAL_1[ULTIMO_CODIGO_DUVAL_1],
      }
    : {
        method: 'Triangulo 1',
        code: '-',
        description: 'Sem coleta com os três gases do triângulo 1',
      },
];

/**
 * Conclusão de um triângulo condicional: o que a tabela normativa diz sobre a
 * coleta mais recente que a figura pôde ler. Sem leitura licenciada não sai
 * veredito - emitir código sobre figura não licenciada é justamente o erro
 * que esta tela cometia ao desenhar os três triângulos para toda amostra.
 */
function conclusaoDe<C extends string>(opcoes: {
  readonly method: string;
  readonly codigos: readonly C[];
  readonly descricoes: Record<C, string>;
  readonly semVeredito: string;
}): DiagnosisConclusion {
  const ultimo = opcoes.codigos.at(-1);
  return ultimo
    ? { method: opcoes.method, code: ultimo, description: opcoes.descricoes[ultimo] }
    : { method: opcoes.method, code: '-', description: opcoes.semVeredito };
}

const COLETAS_FISICO_QUIMICO: readonly AnalysisReading[] = [
    {
      date: DATAS_FQ[0],
      values: {
        neutralizacao: 0.5,
        agua: 25,
        densidade: 0.9,
        fatorDePotencia: 2,
        rigidez: 49,
        tensaoInterfacial: 20,
        cor: 1,
      },
    },
    {
      date: DATAS_FQ[1],
      values: {
        neutralizacao: 0.5,
        agua: 32,
        densidade: 0.9,
        fatorDePotencia: 2.5,
        rigidez: 49,
        tensaoInterfacial: 20,
        cor: 1,
      },
    },
    {
      date: DATAS_FQ[2],
      values: {
        neutralizacao: 0.5,
        agua: 42,
        densidade: 0.9,
        fatorDePotencia: 2,
        rigidez: 60,
        tensaoInterfacial: 19.5,
        cor: 1,
      },
    },
    {
      date: DATAS_FQ[3],
      values: {
        neutralizacao: 0.5,
        agua: 39,
        densidade: 0.9,
        fatorDePotencia: 2,
        rigidez: 48,
        tensaoInterfacial: 18.5,
        cor: 1,
      },
    },
    {
      date: DATAS_FQ[4],
      values: {
        neutralizacao: 0.6,
        agua: 65,
        densidade: 0.9,
        fatorDePotencia: 2.5,
        rigidez: 37,
        tensaoInterfacial: 19,
        cor: 1,
      },
    },
];

// A coleta mais recente é a coluna RESULTADO do laudo; as anteriores são o
// histórico. A tabela e o gráfico leem as mesmas coletas, senão os dois
// divergem sem ninguém notar.
const COLUNAS_FISICO_QUIMICO: readonly AnalysisColumn[] = COLETAS_FISICO_QUIMICO.map(
  (coleta, indice) => ({
    date: coleta.date,
    isCurrentResult: indice === COLETAS_FISICO_QUIMICO.length - 1,
  }),
);

// A série do gráfico não distingue ensaio não detectado de coleta não
// realizada: para a linha, os dois são buraco. A tabela do laudo distingue, e
// a distinção mora aqui. Índice sem marca vale o valor da própria coleta.
const AUSENCIAS_POR_ENSAIO: Readonly<Record<string, readonly (MeasuredValue | undefined)[]>> = {
  neutralizacao: [null, null],
  tensaoInterfacial: ['ND'],
};

function valoresPorColetaDoEnsaio(key: string): readonly MeasuredValue[] {
  const ausencias = AUSENCIAS_POR_ENSAIO[key] ?? [];
  return COLETAS_FISICO_QUIMICO.map((coleta, indice) => {
    const ausencia = ausencias[indice];
    return ausencia !== undefined ? ausencia : (coleta.values[key] ?? null);
  });
}

export const HEALTH_INDICATORS: HealthIndicators = {
  equipmentId: '58836',
  tag: 'TR-B05',
  selectedAnalysis: null,

  physicalChemical: {
    conformity: 'normal',
    verdict: 'APROVADO',
    nominalVoltage: '≤ 72,5 kV',
    columns: COLUNAS_FISICO_QUIMICO,
    analyses: COLETAS_FISICO_QUIMICO,
    tests: [
      {
        key: 'neutralizacao',
        name: 'Índice de Neutralização (mg KOH/g óleo)',
        method: 'NBR 14248',
        limit: 'máx. 0,20',
        valuesByAnalysis: valoresPorColetaDoEnsaio('neutralizacao'),
        classification: 'nao-conforme',
      },
      {
        key: 'agua',
        name: 'Teor de Água (ppm m/m) - Medido',
        method: 'NBR 10710',
        limit: 'máx. 40',
        valuesByAnalysis: valoresPorColetaDoEnsaio('agua'),
        classification: 'normal',
      },
      {
        key: 'densidade',
        name: 'Densidade a 20/4°C (g/mL)',
        method: 'NBR 14065',
        limit: '-',
        valuesByAnalysis: valoresPorColetaDoEnsaio('densidade'),
        classification: 'nao-conforme',
      },
      {
        key: 'fatorDePotencia',
        name: 'Fator de Potência a 100°C (%)',
        method: 'NBR 12133',
        limit: 'máx. 20',
        valuesByAnalysis: valoresPorColetaDoEnsaio('fatorDePotencia'),
        classification: 'alerta',
      },
      {
        key: 'rigidez',
        name: 'Rigidez Dielétrica - Calota (kV)',
        method: 'NBR/IEC 60156',
        limit: 'min. 40',
        valuesByAnalysis: valoresPorColetaDoEnsaio('rigidez'),
        classification: 'normal',
      },
      {
        key: 'tensaoInterfacial',
        name: 'Tensão Interfacial (dina/cm)',
        method: 'NBR 6234',
        limit: 'min. 20',
        valuesByAnalysis: valoresPorColetaDoEnsaio('tensaoInterfacial'),
        classification: 'normal',
      },
    ],
    report: {
      title: 'Laudo das análises físico-químicas',
      paragraphs: [
        'De acordo com a norma ABNT NBR 10576/17, os results obtidos nesta análise indicam que ' +
          'o óleo mineral isolante encontra-se em boas condições físico-químicas.',
        'Sugere-se nova análise no prazo de 1 (um) ano.',
      ],
    },
  },

  dissolvedGases: {
    conformity: 'normal',
    verdict: 'APROVADO',
    analysisDates: ['2025-09-16', '2025-11-27'],
    analyses: COLETAS_DE_GASES,
    gases: [
      { key: 'h2', name: 'Hidrogênio', formula: 'H₂', results: [2028, 12] },
      { key: 'o2', name: 'Oxigênio', formula: 'O₂', results: [11256, 6930] },
      { key: 'n2', name: 'Nitrogênio', formula: 'N₂', results: [37451, 46943] },
      { key: 'ch4', name: 'Metano', formula: 'CH₄', results: [601, 3] },
      { key: 'co', name: 'Monóxido de Carbono', formula: 'CO', results: [1239, 730] },
      { key: 'co2', name: 'Dióxido de Carbono', formula: 'CO₂', results: [1228, 2788] },
      { key: 'c2h4', name: 'Etileno', formula: 'C₂H₄', results: [960, 15] },
      { key: 'c2h6', name: 'Etano', formula: 'C₂H₆', results: [74, 3] },
      { key: 'c2h2', name: 'Acetileno', formula: 'C₂H₂', results: [1297, 'ND'] },
      { key: 'combustiveis', name: 'Total de Gases Combustíveis', formula: '', results: [6199, 763] },
      { key: 'total', name: 'Total de Gases', formula: '', results: [56134, 57424] },
    ],
    report: {
      title: 'Laudo da análise cromatográfica',
      paragraphs: [
        'Baseado nos critérios adotados pelos Institutos Lactec, os resultados obtidos na análise ' +
          'são considerados normais.',
        'Sugere-se nova análise no prazo de 1 (um) ano.',
      ],
    },
  },

  diagnosis: {
    triangles: [
      {
        number: 1,
        leftAxis: 'Methane CH₄ %',
        rightAxis: 'Ethylene C₂H₄ %',
        baseAxis: 'Acetylene C₂H₂ %',
        zones: zonasDuval1Com(CODIGOS_DUVAL_1),
        readings: LEITURAS_DUVAL_1,
        notApplicable: null,
      },
      {
        number: 4,
        leftAxis: 'Hydrogen H₂ %',
        rightAxis: 'Methane CH₄ %',
        baseAxis: 'Ethane C₂H₆ %',
        zones: zonasDuval4Com(CODIGOS_DUVAL_4),
        readings: LEITURAS_DUVAL_4,
        notApplicable: motivoDaFiguraVazia(LEITURAS_DUVAL_4, MOTIVO_DUVAL_4_NAO_APLICAVEL),
      },
      {
        number: 5,
        leftAxis: 'Methane CH₄ %',
        rightAxis: 'Ethylene C₂H₄ %',
        baseAxis: 'Ethane C₂H₆ %',
        zones: zonasDuval5Com(CODIGOS_DUVAL_5),
        readings: LEITURAS_DUVAL_5,
        notApplicable: motivoDaFiguraVazia(LEITURAS_DUVAL_5, MOTIVO_DUVAL_5_NAO_APLICAVEL),
      },
    ],
    pentagon: { zone: 'T2', x: 0, y: 0 },
    ieee: {
      series: [12, 18, 40, 260, 620, 540, 470, 500, 430, 280, 150, 90, 60, 45, 38],
      alarm: 600,
      attention: 400,
    },
    // A legenda sai do mesmo dicionário que o diagnóstico usa: antes dizia que
    // T1/T2 eram "baixa/alta energia", quando T1/T2/T3 são faixas de
    // temperatura (<300 °C, 300-700 °C, >700 °C). Energia é D1/D2.
    iecCodes: CODIGOS_IEC,
    nbr7274: [
      { gas: 'H₂', value: 2028, limit: 1500 },
      { gas: 'CH₄', value: 601, limit: 1000 },
      { gas: 'C₂H₄', value: 960, limit: 800 },
      { gas: 'C₂H₂', value: 400, limit: 700 },
      { gas: 'CO₂', value: 228, limit: 900 },
    ],
    rogers: [
      { row: 'CH₄', column: 'CH₄', result: 'descarga' },
      { row: 'CH₄', column: 'H₂', result: 'descarga' },
      { row: 'CH₄', column: 'C₂H₂', result: 'indeterminado' },
      { row: 'C₂H₂', column: 'CH₄', result: 'descarga' },
      { row: 'C₂H₂', column: 'H₂', result: 'termico' },
      { row: 'C₂H₂', column: 'C₂H₂', result: 'indeterminado' },
      { row: 'CO₂', column: 'CH₄', result: 'indeterminado' },
      { row: 'CO₂', column: 'H₂', result: 'termico' },
      { row: 'CO₂', column: 'C₂H₂', result: 'descarga' },
    ],
    conclusions: [
      ...CONCLUSOES_DUVAL_1,
      // Agora saem do classificador - mas só quando o triângulo 1 licencia a
      // figura. Sem licença não há veredito: a geometria responderia de
      // qualquer jeito, e é essa resposta automática que a norma proíbe ler.
      conclusaoDe({
        method: 'Triangulo 4',
        codigos: CODIGOS_DUVAL_4,
        descricoes: DESCRICAO_DE_DUVAL_4,
        semVeredito:
          motivoDaFiguraVazia(LEITURAS_DUVAL_4, MOTIVO_DUVAL_4_NAO_APLICAVEL) ??
          'Sem coleta com os três gases do triângulo 4',
      }),
      conclusaoDe({
        method: 'Triangulo 5',
        codigos: CODIGOS_DUVAL_5,
        descricoes: DESCRICAO_DE_DUVAL_5,
        semVeredito:
          motivoDaFiguraVazia(LEITURAS_DUVAL_5, MOTIVO_DUVAL_5_NAO_APLICAVEL) ??
          'Sem coleta com os três gases do triângulo 5',
      }),
    ],
  },
  // A tela ainda nao mostra estas duas listas; elas existem no corpo da API e por isso
  // existem no tipo. A massa declara vazio em vez de omitir, para o tipo nao precisar de
  // campo opcional so por causa da demonstracao.
  unreadableResults: [],
  totalizerChecks: [],
};
