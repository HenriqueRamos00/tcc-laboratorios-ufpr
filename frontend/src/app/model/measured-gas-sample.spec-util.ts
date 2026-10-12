// Massa de teste de cromatografia derivada do dado REAL das views do AutoLAB.
//
// ARQUIVO GERADO. Nao edite a mao: regere com
// `tools/gerar-amostra-de-cromatografia.mjs`, que le os dois arquivos JSON
// Lines do adaptador de amostra embutida do backend e recusa gravar quando o
// totalizador publicado de um protocolo não bate exatamente com a soma dos nove
// gases daquele protocolo.
//
// Fonte, por procedência - a distinção é a mesma que o backend faz em
// `ReadMeasuredSample()`, e existe porque teste que afirma sobre número medido
// não pode ler número escrito à mão:
//
//   'medida'        LabTestSample.jsonl, transcrição das views.
//   'demonstracao'  DemonstrationSeries.jsonl, escrito à mão para um
//                   equipamento ter mais de um laudo.
//
// NÃO é código de produção: nada no aplicativo importa este arquivo. Ele existe
// para os testes afirmarem sobre número medido, e não sobre número escolhido por
// conveniência de quem escreve o teste.
//
// O resultado da view tem exatamente três formas e as três significam coisas
// diferentes. `rawResults` guarda a forma que chegou e `gasesInPartsPerMillion`
// guarda o número que entra na conta:
//
//   número  o ensaio foi feito e deu aquilo          -> o próprio número
//   'ND'    o ensaio foi feito e nada foi detectado  -> zero medido
//   null    o ensaio não foi realizado               -> sem medição
//
// Colapsar 'ND' em null faria o laudo com metano, etileno e acetileno todos em
// 'ND' sair como falta de dado, quando o fato é total zero: o triângulo é
// genuinamente incomputável porque nada foi detectado, não porque ninguém mediu.

import type { GasesDoDuval1 } from './duval-1';
import type { GasesDoDuval4 } from './duval-4';
import type { GasesDoDuval5 } from './duval-5';
import type { AnalysisReading, DissolvedGas, DissolvedGasBlock, MeasuredValue } from './health';

/** Os nove gases da cromatografia, na chave que o bloco de gases dissolvidos usa. */
export type GasKey = 'h2' | 'o2' | 'n2' | 'ch4' | 'co' | 'co2' | 'c2h4' | 'c2h6' | 'c2h2';

/** Os dois totalizadores publicados, que NÃO são gases medidos. */
export type TotalizerKey = 'combustibleGases' | 'allGases';

export type SampleProvenance = 'medida' | 'demonstracao';

export interface ChromatographySample {
  readonly protocoloLactec: string;
  readonly identificacao: string;
  /** Pode não ser numérico: existe `111.594/1` na amostra real. */
  readonly serialNumber: string;
  readonly companyName: string;
  readonly analysisDate: string;
  readonly provenance: SampleProvenance;
  readonly rawResults: Readonly<Record<GasKey | TotalizerKey, MeasuredValue>>;
  readonly gasesInPartsPerMillion: Readonly<Record<GasKey, number>>;
  readonly publishedTotalizers: Readonly<Record<TotalizerKey, number>>;
}

/** Ordem de exibição dos gases no laudo impresso. */
export const GAS_KEYS: readonly GasKey[] = [
  'h2',
  'o2',
  'n2',
  'ch4',
  'co',
  'co2',
  'c2h4',
  'c2h6',
  'c2h2',
];

/** Os seis gases que o Total de Gases Combustíveis soma. */
export const COMBUSTIBLE_GAS_KEYS: readonly GasKey[] = ['h2', 'ch4', 'co', 'c2h4', 'c2h6', 'c2h2'];

export const GAS_NAME_BY_KEY: Readonly<Record<GasKey, string>> = {
  h2: 'Hidrogênio',
  o2: 'Oxigênio',
  n2: 'Nitrogênio',
  ch4: 'Metano',
  co: 'Monóxido de Carbono',
  co2: 'Dióxido de Carbono',
  c2h4: 'Etileno',
  c2h6: 'Etano',
  c2h2: 'Acetileno',
};

export const GAS_FORMULA_BY_KEY: Readonly<Record<GasKey, string>> = {
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

export const CHROMATOGRAPHY_SAMPLES: readonly ChromatographySample[] = [
  {
    protocoloLactec: '02114/2025-001',
    identificacao: 'LACTEC-03781/2025',
    serialNumber: '571289',
    companyName: 'EMPRESA DE ENERGIA',
    analysisDate: '2025-09-15',
    provenance: 'demonstracao',
    rawResults: {
      h2: 28,
      o2: 3380,
      n2: 24711,
      ch4: 41,
      co: 104,
      co2: 1842,
      c2h4: 18,
      c2h6: 96,
      c2h2: 'ND',
      combustibleGases: 287,
      allGases: 30220,
    },
    gasesInPartsPerMillion: {
      h2: 28,
      o2: 3380,
      n2: 24711,
      ch4: 41,
      co: 104,
      co2: 1842,
      c2h4: 18,
      c2h6: 96,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 287,
      allGases: 30220,
    },
  },
  {
    protocoloLactec: '02630/2025-001',
    identificacao: 'LACTEC-04502/2025',
    serialNumber: '571289',
    companyName: 'EMPRESA DE ENERGIA',
    analysisDate: '2025-12-10',
    provenance: 'demonstracao',
    rawResults: {
      h2: 35,
      o2: 2807,
      n2: 25264,
      ch4: 52,
      co: 118,
      co2: 2093,
      c2h4: 22,
      c2h6: 109,
      c2h2: 'ND',
      combustibleGases: 336,
      allGases: 30500,
    },
    gasesInPartsPerMillion: {
      h2: 35,
      o2: 2807,
      n2: 25264,
      ch4: 52,
      co: 118,
      co2: 2093,
      c2h4: 22,
      c2h6: 109,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 336,
      allGases: 30500,
    },
  },
  {
    protocoloLactec: '00712/2026-001',
    identificacao: 'LACTEC-01188/2026',
    serialNumber: '571289',
    companyName: 'EMPRESA DE ENERGIA',
    analysisDate: '2026-03-18',
    provenance: 'demonstracao',
    rawResults: {
      h2: 48,
      o2: 2258,
      n2: 25702,
      ch4: 66,
      co: 131,
      co2: 2385,
      c2h4: 27,
      c2h6: 127,
      c2h2: 'ND',
      combustibleGases: 399,
      allGases: 30744,
    },
    gasesInPartsPerMillion: {
      h2: 48,
      o2: 2258,
      n2: 25702,
      ch4: 66,
      co: 131,
      co2: 2385,
      c2h4: 27,
      c2h6: 127,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 399,
      allGases: 30744,
    },
  },
  {
    protocoloLactec: '01417/2026-001',
    identificacao: 'LACTEC-02272/2026',
    serialNumber: '571289',
    companyName: 'EMPRESA DE ENERGIA',
    analysisDate: '2026-06-23',
    provenance: 'medida',
    rawResults: {
      h2: 60,
      o2: 1969,
      n2: 26028,
      ch4: 77,
      co: 139,
      co2: 2617,
      c2h4: 33,
      c2h6: 140,
      c2h2: 'ND',
      combustibleGases: 449,
      allGases: 31063,
    },
    gasesInPartsPerMillion: {
      h2: 60,
      o2: 1969,
      n2: 26028,
      ch4: 77,
      co: 139,
      co2: 2617,
      c2h4: 33,
      c2h6: 140,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 449,
      allGases: 31063,
    },
  },
  {
    protocoloLactec: '01439/2026-004',
    identificacao: 'LACTEC-02465/2026',
    serialNumber: '884120',
    companyName: 'EMPRESA DE ENERGIA',
    analysisDate: '2026-07-03',
    provenance: 'medida',
    rawResults: {
      h2: 2,
      o2: 1941,
      n2: 8747,
      ch4: 'ND',
      co: 10,
      co2: 82,
      c2h4: 'ND',
      c2h6: 'ND',
      c2h2: 'ND',
      combustibleGases: 12,
      allGases: 10782,
    },
    gasesInPartsPerMillion: {
      h2: 2,
      o2: 1941,
      n2: 8747,
      ch4: 0,
      co: 10,
      co2: 82,
      c2h4: 0,
      c2h6: 0,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 12,
      allGases: 10782,
    },
  },
  {
    protocoloLactec: '01563/2026-018',
    identificacao: 'LACTEC-02665/2026',
    serialNumber: '5233',
    companyName: 'DISTRIBUIDORA DE ENERGIA',
    analysisDate: '2026-07-10',
    provenance: 'medida',
    rawResults: {
      h2: 5,
      o2: 26208,
      n2: 62259,
      ch4: 1,
      co: 50,
      co2: 1333,
      c2h4: 5,
      c2h6: 1,
      c2h2: 'ND',
      combustibleGases: 62,
      allGases: 89862,
    },
    gasesInPartsPerMillion: {
      h2: 5,
      o2: 26208,
      n2: 62259,
      ch4: 1,
      co: 50,
      co2: 1333,
      c2h4: 5,
      c2h6: 1,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 62,
      allGases: 89862,
    },
  },
  {
    protocoloLactec: '01718/2026-005',
    identificacao: 'LACTEC-02962/2026',
    serialNumber: '36170301',
    companyName: 'DISTRIBUIDORA DE ENERGIA',
    analysisDate: '2026-07-24',
    provenance: 'medida',
    rawResults: {
      h2: 8,
      o2: 10152,
      n2: 74372,
      ch4: 22,
      co: 1648,
      co2: 9252,
      c2h4: 4,
      c2h6: 5,
      c2h2: 'ND',
      combustibleGases: 1687,
      allGases: 95463,
    },
    gasesInPartsPerMillion: {
      h2: 8,
      o2: 10152,
      n2: 74372,
      ch4: 22,
      co: 1648,
      co2: 9252,
      c2h4: 4,
      c2h6: 5,
      c2h2: 0,
    },
    publishedTotalizers: {
      combustibleGases: 1687,
      allGases: 95463,
    },
  },
];

/**
 * Os gases de cada triangulo, lidos da coleta. Existem aqui, e não soltos em
 * cada teste, porque troca de eixo é invisível ao compilador: `ch4` e `c2h4`
 * tem o mesmo tipo, e um extrator trocado passa batido. Quem pega a troca são as
 * coordenadas afirmadas nos testes, uma por uma.
 */
export function duval1GasesOf(sample: ChromatographySample): GasesDoDuval1 {
  const gases = sample.gasesInPartsPerMillion;
  return { ch4: gases.ch4, c2h4: gases.c2h4, c2h2: gases.c2h2 };
}

export function duval4GasesOf(sample: ChromatographySample): GasesDoDuval4 {
  const gases = sample.gasesInPartsPerMillion;
  return { h2: gases.h2, ch4: gases.ch4, c2h6: gases.c2h6 };
}

export function duval5GasesOf(sample: ChromatographySample): GasesDoDuval5 {
  const gases = sample.gasesInPartsPerMillion;
  return { ch4: gases.ch4, c2h4: gases.c2h4, c2h6: gases.c2h6 };
}

/** Somente os protocolos transcritos das views, sem os escritos à mão. */
export const MEASURED_CHROMATOGRAPHY_SAMPLES: readonly ChromatographySample[] =
  CHROMATOGRAPHY_SAMPLES.filter((sample) => sample.provenance === 'medida');

/**
 * O protocolo pedido, ou exceção. Devolver `undefined` deixaria o teste afirmar
 * sobre nada e passar: protocolo que sumiu da amostra tem que acender a luz na
 * hora, com o nome do que faltou.
 */
export function chromatographySampleOf(protocoloLactec: string): ChromatographySample {
  const found = CHROMATOGRAPHY_SAMPLES.find((sample) => sample.protocoloLactec === protocoloLactec);
  if (!found) throw new Error(`protocolo ${protocoloLactec} não está na massa de cromatografia`);
  return found;
}

/** Os laudos de um equipamento, em ordem cronológica. */
export function chromatographySamplesOfEquipment(
  serialNumber: string,
): readonly ChromatographySample[] {
  return CHROMATOGRAPHY_SAMPLES.filter((sample) => sample.serialNumber === serialNumber);
}

/**
 * O bloco de gases dissolvidos como o fio o entrega, montado a partir dos
 * protocolos pedidos. `conformity` e `verdict` saem nulos porque o nucleo
 * normativo não roda no backend: o AutoLAB entrega o valor medido, não o
 * parecer, e nulo aqui é ensaio ainda sem parecer.
 */
export function dissolvedGasBlockOf(
  samples: readonly ChromatographySample[],
): DissolvedGasBlock {
  const analyses: readonly AnalysisReading[] = samples.map((sample) => ({
    date: sample.analysisDate,
    values: Object.fromEntries(
      GAS_KEYS.map((key) => [key, sample.gasesInPartsPerMillion[key]]),
    ) as Readonly<Record<string, number | null>>,
  }));
  const gases: readonly DissolvedGas[] = GAS_KEYS.map((key) => ({
    key,
    name: GAS_NAME_BY_KEY[key],
    formula: GAS_FORMULA_BY_KEY[key],
    results: samples.map((sample) => sample.rawResults[key]),
  }));
  return {
    conformity: null,
    verdict: null,
    analysisDates: samples.map((sample) => sample.analysisDate),
    analyses,
    gases,
    report: {
      title: 'Laudo da análise cromatográfica',
      paragraphs: [],
    },
  };
}
