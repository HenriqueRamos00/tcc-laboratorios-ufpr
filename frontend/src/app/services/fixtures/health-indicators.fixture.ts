// Resultados, datas e traçado dos gráficos vêm das telas aprovadas.
//
// A massa espelha o fio: `diagnosis` chega nulo, como o backend serve hoje, e
// quem calcula os métodos é o `DissolvedGasDiagnosisService`, a partir dos gases
// dissolvidos que esta mesma massa publica. A derivação dos triângulos de Duval
// morava aqui e virou código de produção: massa com derivação própria afirma
// sobre uma conta que a tela não faz, e as duas divergem sem ninguém notar.
//
// Pelo mesmo motivo saíram daqui o pentágono, a série do IEEE, as barras da
// NBR 7274 e a grade de Rogers, que eram valores escritos à mão sem relação com
// os gases da própria massa.

import type {
  AnalysisReading,
  AnalysisColumn,
  HealthIndicators,
  MeasuredValue,
} from '@/app/model/health';


// A API entrega toda data em ISO; a tela formata na entrada. Fixture com data já
// formatada esconderia exatamente o passo que a tela precisa dar.
const DATAS_FQ = ['2021-04-20', '2022-05-20', '2023-09-19', '2024-04-26', '2025-10-26'];



// --- Coletas de gases dissolvidos -------------------------------------------
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
  (coleta, index) => ({
    date: coleta.date,
    isCurrentResult: index === COLETAS_FISICO_QUIMICO.length - 1,
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
  return COLETAS_FISICO_QUIMICO.map((coleta, index) => {
    const ausencia = ausencias[index];
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

  // O corpo da API serve `diagnosis` nulo, e é o `DissolvedGasDiagnosisService` que
  // calcula os métodos a partir dos gases dissolvidos acima. Declarar nulo aqui é o
  // que faz a massa exercitar o caminho que a tela de verdade percorre.
  diagnosis: null,
  // A tela ainda nao mostra estas duas listas; elas existem no corpo da API e por isso
  // existem no tipo. A massa declara vazio em vez de omitir, para o tipo nao precisar de
  // campo opcional so por causa da demonstracao.
  unreadableResults: [],
  totalizerChecks: [],
};
