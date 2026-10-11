import {
  classificarDuval1,
  duval1,
  gasAusenteDoDuval1,
  type CodigoDeDuval1,
} from './duval-1';
import { aplicaSeDuval4, classificarDuval4, duval4 } from './duval-4';
import { aplicaSeDuval5, classificarDuval5, duval5 } from './duval-5';
import { DissolvedGasDiagnosisService } from '@/app/services/dissolved-gas-diagnosis.service';

import type { TernaryPoint } from './health';
import {
  chromatographySampleOf,
  chromatographySamplesOfEquipment,
  dissolvedGasBlockOf,
  duval1GasesOf,
  duval4GasesOf,
  duval5GasesOf,
  COMBUSTIBLE_GAS_KEYS,
  CHROMATOGRAPHY_SAMPLES,
  GAS_KEYS,
  MEASURED_CHROMATOGRAPHY_SAMPLES,
  type ChromatographySample,
} from './measured-gas-sample.spec-util';

// Os metodos de diagnostico contra o dado REAL das views do AutoLAB, pela massa
// gerada em `measured-gas-sample.spec-util.ts`. Cada numero afirmado aqui e
// numero medido: nenhum valor foi escolhido para fazer a conta fechar.
//
// O que a amostra real cobre, e o que ela nao cobre: os quatro protocolos de
// cromatografia transcritos das views dao T1, T2, T3 e um caso incomputavel no
// triangulo 1, e nenhum D1, D2 ou DT, porque o acetileno nao foi detectado em
// nenhum deles. As fronteiras que o dado real nao visita ficam em
// `duval-normative-boundaries.spec.ts`, por valor-limite.

/** Protocolo com acetileno nao detectado e falha termica T2. */
const PROTOCOL_T2 = '01417/2026-001';

/** Protocolo com metano, etileno e acetileno todos em ND: total zero. */
const PROTOCOL_ALL_ND = '01439/2026-004';

/** Protocolo com falha termica T3, a mais quente da amostra. */
const PROTOCOL_T3 = '01563/2026-018';

/** Protocolo com falha termica T1, a mais fria da amostra. */
const PROTOCOL_T1 = '01718/2026-005';

const EQUIPMENT_WITH_FOUR_REPORTS = '571289';

function expectPointAt(point: TernaryPoint | null, expected: TernaryPoint): void {
  expect(point).withContext('a coleta nao virou ponto no triangulo').not.toBeNull();
  expect(point!.a).toBeCloseTo(expected.a, 6);
  expect(point!.b).toBeCloseTo(expected.b, 6);
  expect(point!.c).toBeCloseTo(expected.c, 6);
  expect(point!.a + point!.b + point!.c).toBeCloseTo(100, 9);
  for (const coordinate of [point!.a, point!.b, point!.c]) {
    expect(coordinate).toBeGreaterThanOrEqual(0);
    expect(coordinate).toBeLessThanOrEqual(100);
  }
}

function duval1CodeOf(sample: ChromatographySample): CodigoDeDuval1 | null {
  const point = duval1(duval1GasesOf(sample));
  return point ? classificarDuval1(point) : null;
}

describe('Massa de cromatografia derivada do dado real', () => {
  it('traz os sete protocolos de cromatografia em ordem cronologica, quatro deles medidos', () => {
    expect(CHROMATOGRAPHY_SAMPLES.length).toBe(7);
    expect(MEASURED_CHROMATOGRAPHY_SAMPLES.length).toBe(4);
    const dates = CHROMATOGRAPHY_SAMPLES.map((sample) => sample.analysisDate);
    expect([...dates].sort()).toEqual(dates);
  });

  it('publica totalizador igual a soma exata dos gases medidos, em todo protocolo', () => {
    for (const sample of CHROMATOGRAPHY_SAMPLES) {
      const allGases = GAS_KEYS.reduce(
        (total, key) => total + sample.gasesInPartsPerMillion[key],
        0,
      );
      const combustibleGases = COMBUSTIBLE_GAS_KEYS.reduce(
        (total, key) => total + sample.gasesInPartsPerMillion[key],
        0,
      );
      expect(allGases)
        .withContext(`Total de Gases do protocolo ${sample.protocoloLactec}`)
        .toBe(sample.publishedTotalizers.allGases);
      expect(combustibleGases)
        .withContext(`Total de Gases Combustiveis do protocolo ${sample.protocoloLactec}`)
        .toBe(sample.publishedTotalizers.combustibleGases);
    }
  });

  it('entrega o acetileno nao detectado como ND no fio e como zero na conta', () => {
    const block = dissolvedGasBlockOf(chromatographySamplesOfEquipment(EQUIPMENT_WITH_FOUR_REPORTS));
    expect(block.analysisDates).toEqual(['2025-09-15', '2025-12-10', '2026-03-18', '2026-06-23']);
    expect(block.gases.length).toBe(9);
    const acetylene = block.gases.find((gas) => gas.key === 'c2h2');
    expect(acetylene?.results).toEqual(['ND', 'ND', 'ND', 'ND']);
    for (const analysis of block.analyses) {
      expect(analysis.values['c2h2']).toBe(0);
    }
  });
});

describe('Triangulo de Duval 1 sobre o dado real', () => {
  it('poe o laudo de 2026-06-23 na base do triangulo, onde o acetileno nao detectado vale zero', () => {
    const sample = chromatographySampleOf(PROTOCOL_T2);
    expect(sample.rawResults.c2h2).toBe('ND');
    expect(sample.gasesInPartsPerMillion.c2h2).toBe(0);
    expectPointAt(duval1(duval1GasesOf(sample)), { a: 70, b: 30, c: 0 });
  });

  it('diagnostica falha termica distinta em cada protocolo medido que da para computar', () => {
    expect(duval1CodeOf(chromatographySampleOf(PROTOCOL_T1))).toBe('T1');
    expect(duval1CodeOf(chromatographySampleOf(PROTOCOL_T2))).toBe('T2');
    expect(duval1CodeOf(chromatographySampleOf(PROTOCOL_T3))).toBe('T3');
  });

  it('nao acha descarga em protocolo medido nenhum, porque o acetileno nunca foi detectado', () => {
    for (const sample of MEASURED_CHROMATOGRAPHY_SAMPLES) {
      expect(sample.gasesInPartsPerMillion.c2h2)
        .withContext(`acetileno do protocolo ${sample.protocoloLactec}`)
        .toBe(0);
      expect(['D1', 'D2', 'DT'])
        .withContext(`veredito do protocolo ${sample.protocoloLactec}`)
        .not.toContain(duval1CodeOf(sample) ?? 'sem veredito');
    }
  });

  it('nao computa o laudo com metano, etileno e acetileno em ND, e nao culpa gas faltante', () => {
    const sample = chromatographySampleOf(PROTOCOL_ALL_ND);
    expect(sample.rawResults.ch4).toBe('ND');
    expect(sample.rawResults.c2h4).toBe('ND');
    expect(sample.rawResults.c2h2).toBe('ND');
    expect(duval1(duval1GasesOf(sample))).toBeNull();
    expect(gasAusenteDoDuval1(duval1GasesOf(sample))).toBeNull();
  });

  it('computa os quatro laudos do equipamento 571289, inclusive os tres sem acetileno detectado', () => {
    const samples = chromatographySamplesOfEquipment(EQUIPMENT_WITH_FOUR_REPORTS);
    expect(samples.length).toBe(4);
    for (const sample of samples) {
      expect(duval1(duval1GasesOf(sample)))
        .withContext(`protocolo ${sample.protocoloLactec}`)
        .not.toBeNull();
    }
  });
});

describe('Triangulo de Duval 4 sobre o dado real', () => {
  it('le o laudo T1 de 2026-07-24 e o poe na zona de carbonizacao do papel', () => {
    const sample = chromatographySampleOf(PROTOCOL_T1);
    expect(aplicaSeDuval4(duval1CodeOf(sample))).toBeTrue();
    const point = duval4(duval4GasesOf(sample));
    expectPointAt(point, {
      a: 22.857142857142858,
      b: 62.857142857142854,
      c: 14.285714285714285,
    });
    expect(classificarDuval4(point!)).toBe('C');
  });

  it('le o laudo T2 de 2026-06-23 e nao determina a falha, com o etano acima de 46 por cento', () => {
    const sample = chromatographySampleOf(PROTOCOL_T2);
    expect(aplicaSeDuval4(duval1CodeOf(sample))).toBeTrue();
    const point = duval4(duval4GasesOf(sample));
    expectPointAt(point, {
      a: 21.660649819494584,
      b: 27.79783393501805,
      c: 50.54151624548736,
    });
    expect(classificarDuval4(point!)).toBe('ND');
  });

  it('recusa o laudo T3 de 2026-07-10, embora a geometria tenha resposta para ele', () => {
    const sample = chromatographySampleOf(PROTOCOL_T3);
    expect(duval1CodeOf(sample)).toBe('T3');
    expect(aplicaSeDuval4(duval1CodeOf(sample))).toBeFalse();
    const point = duval4(duval4GasesOf(sample));
    expect(point).not.toBeNull();
    expect(classificarDuval4(point!)).toBe('S');
  });

  it('recusa o laudo sem veredito no triangulo 1, embora o hidrogenio tenha sido medido', () => {
    const sample = chromatographySampleOf(PROTOCOL_ALL_ND);
    expect(sample.gasesInPartsPerMillion.h2).toBe(2);
    expect(aplicaSeDuval4(duval1CodeOf(sample))).toBeFalse();
    const point = duval4(duval4GasesOf(sample));
    expectPointAt(point, { a: 100, b: 0, c: 0 });
    expect(classificarDuval4(point!)).toBe('S');
  });
});

describe('Triangulo de Duval 5 sobre o dado real', () => {
  it('le o laudo T3 de 2026-07-10 e o confirma como falha termica acima de 700 graus', () => {
    const sample = chromatographySampleOf(PROTOCOL_T3);
    expect(aplicaSeDuval5(duval1CodeOf(sample))).toBeTrue();
    const point = duval5(duval5GasesOf(sample));
    expectPointAt(point, {
      a: 14.285714285714285,
      b: 71.42857142857143,
      c: 14.285714285714285,
    });
    expect(classificarDuval5(point!)).toBe('T3');
  });

  it('le o laudo T2 de 2026-06-23 e nao determina a falha, com o etano acima de 30 por cento', () => {
    const sample = chromatographySampleOf(PROTOCOL_T2);
    expect(aplicaSeDuval5(duval1CodeOf(sample))).toBeTrue();
    const point = duval5(duval5GasesOf(sample));
    expectPointAt(point, { a: 30.8, b: 13.2, c: 56 });
    expect(classificarDuval5(point!)).toBe('ND');
  });

  it('recusa o laudo T1 de 2026-07-24, embora a geometria tenha resposta para ele', () => {
    const sample = chromatographySampleOf(PROTOCOL_T1);
    expect(duval1CodeOf(sample)).toBe('T1');
    expect(aplicaSeDuval5(duval1CodeOf(sample))).toBeFalse();
    const point = duval5(duval5GasesOf(sample));
    expect(point).not.toBeNull();
    expect(classificarDuval5(point!)).toBe('C');
  });

  it('nao computa o laudo com metano, etileno e etano em ND, porque o total e zero', () => {
    const sample = chromatographySampleOf(PROTOCOL_ALL_ND);
    expect(sample.gasesInPartsPerMillion.ch4).toBe(0);
    expect(sample.gasesInPartsPerMillion.c2h4).toBe(0);
    expect(sample.gasesInPartsPerMillion.c2h6).toBe(0);
    expect(duval5(duval5GasesOf(sample))).toBeNull();
  });
});

// Os quatro metodos abaixo ainda nao tem algoritmo no frontend: o pentagono e
// so desenho, e NBR 7274 / IEC 60599, Rogers e IEEE C57.104 chegam chumbados na
// massa de demonstracao. Os casos ficam escritos, com a entrada medida que cada
// um deve consumir, e pendentes em vez de vermelhos: suite vermelha por falta de
// implementacao de outro agente nao distingue regressao de trabalho em curso.
//
// A entrada medida de cada um, do protocolo 01417/2026-001:
//   pentagono        H2 60, CH4 77, C2H6 140, C2H4 33, C2H2 0
//   NBR 7274 / IEC   C2H2/C2H4 = 0, CH4/H2 = 1,2833..., C2H4/C2H6 = 0,2357...
//   Rogers           CH4/H2 = 1,2833..., C2H6/CH4 = 1,8181..., C2H4/C2H6 = 0,2357...,
//                    C2H2/C2H4 = 0
//   IEEE C57.104     serie de gases combustiveis do equipamento 571289, nas
//                    quatro datas: 287, 336, 399 e 449 ppm
describe('Métodos que o dado medido alcança agora que o diagnóstico está ligado', () => {
  // O divisor zero não é hipótese: no laudo medido 01439/2026-004, etileno, etano e
  // acetileno saíram ND, então duas das três razões da IEC ficam com zero embaixo.
  // Dividir ali devolveria infinito, e infinito num comparador de faixa vira o
  // diagnóstico mais grave da tabela sem ninguém ter medido nada.
  it('recusa a razão com divisor zero, e diz qual gás não foi detectado', () => {
    const service = new DissolvedGasDiagnosisService();
    const diagnosis = service.diagnose(dissolvedGasBlockOf([chromatographySampleOf('01439/2026-004')]));

    expect(diagnosis).not.toBeNull();
    if (!diagnosis) return;

    const porDivisorZero = diagnosis.iec60599.ratios.filter(
      (ratio) => ratio.kind === 'denominatorNotDetected',
    );

    expect(porDivisorZero.length).toBe(2);
    expect(porDivisorZero.map((ratio) => ratio.kind === 'denominatorNotDetected' && ratio.denominator))
      .toEqual(['C₂H₄', 'C₂H₆']);

    const calculada = diagnosis.iec60599.ratios.find((ratio) => ratio.kind === 'computed');

    expect(calculada?.kind === 'computed' && calculada.value).toBe(0);
  });

  // O totalizador que o laboratório publicou é a prova independente: a soma dos seis
  // combustíveis tem que reproduzir o número que veio no laudo, sem o portal saber
  // dele. Nesse protocolo o publicado é 12, e os gases medidos somam 2 + 10.
  it('reproduz o total de gases combustíveis que o laudo publicou', () => {
    const amostra = chromatographySampleOf('01439/2026-004');
    const service = new DissolvedGasDiagnosisService();
    const diagnosis = service.diagnose(dissolvedGasBlockOf([amostra]));

    const total = diagnosis?.ieee.readings.at(-1)?.total ?? null;

    expect(total).toBe(amostra.publishedTotalizers.combustibleGases);
    expect(total).toBe(12);
  });

  // Quatro coletas do mesmo transformador, em ordem cronológica. A série vem do gás
  // medido, e cada ponto tem que bater com o totalizador daquele laudo.
  it('acompanha a tendência de combustíveis do 571289 nas quatro coletas', () => {
    const amostras = chromatographySamplesOfEquipment('571289');
    const service = new DissolvedGasDiagnosisService();
    const diagnosis = service.diagnose(dissolvedGasBlockOf(amostras));

    expect(diagnosis?.ieee.readings.map((leitura) => leitura.total))
      .toEqual(amostras.map((amostra) => amostra.publishedTotalizers.combustibleGases));
    expect(diagnosis?.ieee.readings.map((leitura) => leitura.total)).toEqual([287, 336, 399, 449]);
  });
});

// Os três métodos abaixo não dependem de esforço, e sim de conteúdo normativo que
// não está neste repositório: as coordenadas de zona do pentágono, a tabela de
// códigos da IEC 60599 e a tabela de faixas de Rogers. Enquanto elas não existirem,
// o portal publica a medição e declara o veredito como não avaliado, e escrever
// aqui um número de memória seria pior que a ausência declarada.
describe('Métodos que dependem de tabela normativa ausente do repositório', () => {
  it('não atribui zona do pentágono, e declara o motivo', () => {
    const service = new DissolvedGasDiagnosisService();
    const diagnosis = service.diagnose(dissolvedGasBlockOf([chromatographySampleOf('01417/2026-001')]));

    expect(diagnosis?.pentagon.zone).toBeNull();
    expect(diagnosis?.pentagon.percentages.length).toBeGreaterThan(0);
  });

  it('não atribui código de falha da IEC 60599 nem de Rogers, e declara o motivo', () => {
    const service = new DissolvedGasDiagnosisService();
    const diagnosis = service.diagnose(dissolvedGasBlockOf([chromatographySampleOf('01417/2026-001')]));

    expect(diagnosis?.iec60599.faultCode).toBeNull();
    expect(diagnosis?.iec60599.faultCodeNotAssessed).not.toBeNull();
    expect(diagnosis?.rogers.faultCode).toBeNull();
    expect(diagnosis?.rogers.faultCodeNotAssessed).not.toBeNull();
  });

  it('não atribui limiar de alarme nem de atenção da IEEE C57.104', () => {
    const service = new DissolvedGasDiagnosisService();
    const diagnosis = service.diagnose(dissolvedGasBlockOf([chromatographySampleOf('01417/2026-001')]));

    expect(diagnosis?.ieee.alarm).toBeNull();
    expect(diagnosis?.ieee.attention).toBeNull();
  });
});
