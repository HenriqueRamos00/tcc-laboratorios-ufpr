import { TestBed } from '@angular/core/testing';

import { NOT_ASSESSED_LABEL,
  type GasRatioReading, type DissolvedGasBlock, type MeasuredValue } from '@/app/model/health';
import type { OilType } from '@/app/model/insulating-oil';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';

import {
  DIAGNOSIS_METHODS,
  DissolvedGasDiagnosisService,
  METHOD_NOT_APPLICABLE_TO_OIL,
  OIL_TYPE_NOT_INFORMED,
} from './dissolved-gas-diagnosis.service';

const MINERAL: OilType = { kind: 'mineral', reported: 'Óleo Mineral Isolante' };

// O backend serve `diagnosis: null` e os nove gases viajam no mesmo corpo: estes
// casos provam que cada um dos sete métodos sai do gás medido, e que o que falta
// de insumo normativo sai declarado em vez de sair inventado.
//
// A massa dos casos é a coluna real transcrita das views do AutoLAB (ver o
// recorte em `health-indicators.fixture.ts`), e as variações mexem num gás só,
// para o caso dizer qual decisão está sendo exercitada.

const FORMULA_BY_KEY: Readonly<Record<string, string>> = {
  h2: 'H₂',
  ch4: 'CH₄',
  c2h6: 'C₂H₆',
  c2h4: 'C₂H₄',
  c2h2: 'C₂H₂',
  co: 'CO',
  co2: 'CO₂',
  o2: 'O₂',
  n2: 'N₂',
};

type ResultsByGas = Readonly<Record<string, readonly MeasuredValue[]>>;

/** A coleta medida: os nove gases dos sete protocolos de cromatografia. */
const MEASURED_COLLECTION: ResultsByGas = {
  h2: [2028],
  o2: [11256],
  n2: [37451],
  ch4: [601],
  co: [1239],
  co2: [1228],
  c2h4: [960],
  c2h6: [74],
  c2h2: [1297],
};

/** O totalizador que o próprio laudo publica para a coleta medida acima. */
// A massa desta suíte é de demonstração, escrita à mão: bater com o totalizador dela
// prova consistência interna, não aritmética contra laudo. A prova contra dado medido
// de verdade está em `diagnosis-measured-sample.spec.ts`, no protocolo 01439/2026-004,
// onde o laboratório publicou 12 e os seis combustíveis somam 12.
const DEMONSTRATION_COMBUSTIBLE_TOTAL = 6199;

function gasBlock(dates: readonly string[], results: ResultsByGas): DissolvedGasBlock {
  return {
    conformity: null,
    verdict: null,
    analysisDates: dates,
    analyses: dates.map((date, index) => ({
      date,
      values: Object.fromEntries(
        Object.entries(results).map(([key, values]) => [
          key,
          typeof values[index] === 'number' ? (values[index] as number) : null,
        ]),
      ),
    })),
    gases: Object.entries(results).map(([key, values]) => ({
      key,
      name: key,
      formula: FORMULA_BY_KEY[key],
      results: values,
    })),
    reports: [],
  };
}

function collectionWith(changes: ResultsByGas): DissolvedGasBlock {
  return gasBlock(['2025-10-26'], { ...MEASURED_COLLECTION, ...changes });
}

function diagnosisOf(block: DissolvedGasBlock) {
  const diagnosis = TestBed.inject(DissolvedGasDiagnosisService).diagnose(block, MINERAL);
  if (diagnosis?.kind !== 'applied') throw new Error('A coleta deveria render diagnóstico e não rendeu');
  return diagnosis;
}

function outcomeOf(block: DissolvedGasBlock, oil: OilType = MINERAL) {
  const outcome = TestBed.inject(DissolvedGasDiagnosisService).diagnose(block, oil);
  if (!outcome) throw new Error('A coleta deveria render um desfecho e não rendeu');
  return outcome;
}

function triangleOf(block: DissolvedGasBlock, number: 1 | 4 | 5) {
  const triangle = diagnosisOf(block).triangles.find((figure) => figure.number === number);
  if (!triangle) throw new Error(`O triângulo ${number} deveria existir e não existe`);
  return triangle;
}

function conclusionOf(block: DissolvedGasBlock, method: string) {
  return diagnosisOf(block).conclusions.find((conclusion) => conclusion.method === method);
}

// Coleta que o triângulo 1 classifica como térmica abaixo de 300 °C, que é uma
// das três falhas que licenciam o triângulo 4.
const THERMAL_COLLECTION: ResultsByGas = { ch4: [100], c2h4: [10], c2h2: ['ND'], h2: [50], c2h6: [20] };


/**
 * A razão é união discriminada, então o teste estreita pelo caso em vez de ler
 * um campo que só existe num dos braços. Afirmar sobre `kind` prende o desfecho;
 * afirmar sobre a frase prenderia a redação da tela.
 */
function computedRatio(method: { readonly ratios: readonly GasRatioReading[] }, position: number) {
  const ratio = method.ratios[position];

  if (ratio.kind !== 'computed') throw new Error(`Razão ${position} não foi calculada.`);

  return ratio.value;
}

describe('DissolvedGasDiagnosisService, triângulos de Duval', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('plota a coleta completa no triângulo 1 nas proporções dos três gases', () => {
    const [reading] = triangleOf(collectionWith({}), 1).readings;

    expect(reading.kind).toBe('plotada');
    if (reading.kind !== 'plotada') return;
    expect(reading.date).toBe('2025-10-26');
    expect(reading.point.a).toBeCloseTo((601 / 2858) * 100, 6);
    expect(reading.point.b).toBeCloseTo((960 / 2858) * 100, 6);
    expect(reading.point.c).toBeCloseTo((1297 / 2858) * 100, 6);
  });

  it('acende no triângulo 1 só a zona que a tabela normativa dá à coleta', () => {
    const highlighted = triangleOf(collectionWith({}), 1)
      .zones.filter((zone) => zone.highlighted)
      .map((zone) => zone.code);

    expect(highlighted).toEqual(['D2']);
  });

  // "ND" é ensaio feito sem detecção: entra como zero medido, que é ponto
  // legítimo na base do triângulo. Virar lacuna faria a tela afirmar ausência de
  // ensaio onde houve ensaio.
  it('plota a coleta com acetileno não detectado, na base do triângulo', () => {
    const [reading] = triangleOf(collectionWith({ c2h2: ['ND'] }), 1).readings;

    expect(reading.kind).toBe('plotada');
    if (reading.kind !== 'plotada') return;
    expect(reading.point.c).toBe(0);
  });

  it('não plota a coleta sem ensaio de acetileno, e diz qual gás faltou', () => {
    const [reading] = triangleOf(collectionWith({ c2h2: [null] }), 1).readings;

    expect(reading.kind).toBe('incomputavel');
    if (reading.kind !== 'incomputavel') return;
    expect(reading.missingGas).toBe('C₂H₂');
  });

  // O caso real do laudo LACTEC-02465/2026: metano, etileno e acetileno em "ND".
  // O triângulo é incomputável por não haver gás, não por não haver ensaio, e a
  // tela não pode culpar o laboratório por uma coleta que ele fez.
  // Os três gases medidos e os três em ND não é falta de ensaio: é zero sobre zero,
  // e zero sobre zero não é proporção. Carregar isso como `incomputavel` com a frase
  // "gás detectado" no campo do gás ausente culpa o laboratório por um ensaio que ele
  // fez. É o caso real do protocolo 01439/2026-004.
  it('separa a coleta sem proporção da coleta que ficou sem ensaio', () => {
    const [semProporcao] = triangleOf(
      collectionWith({ ch4: ['ND'], c2h4: ['ND'], c2h2: ['ND'] }),
      1,
    ).readings;

    expect(semProporcao.kind).toBe('semProporcao');

    const [semEnsaio] = triangleOf(
      collectionWith({ ch4: ['ND'], c2h4: ['ND'], c2h2: [null] }),
      1,
    ).readings;

    expect(semEnsaio.kind).toBe('incomputavel');
    if (semEnsaio.kind !== 'incomputavel') return;
    expect(semEnsaio.missingGas).not.toBe('gás detectado');
  });

  it('não licencia os triângulos 4 e 5 na coleta que o triângulo 1 chama de descarga', () => {
    const diagnosis = diagnosisOf(collectionWith({}));
    const conditional = diagnosis.triangles.filter((figure) => figure.number !== 1);

    expect(conditional.map((figure) => figure.number)).toEqual([4, 5]);
    for (const figure of conditional) {
      expect(figure.readings.map((reading) => reading.kind))
        .withContext(`triângulo ${figure.number}`)
        .toEqual(['naoAplicavel']);
      expect(figure.notApplicable).withContext(`triângulo ${figure.number}`).toContain('só se aplica');
    }
  });

  it('licencia o triângulo 4 na coleta que o triângulo 1 chama de falha térmica leve', () => {
    const block = collectionWith(THERMAL_COLLECTION);

    expect(conclusionOf(block, 'Triângulo 1')?.code).toBe('T1');
    const [reading] = triangleOf(block, 4).readings;
    expect(reading.kind).toBe('plotada');
    if (reading.kind !== 'plotada') return;
    expect(reading.point.a).toBeCloseTo((50 / 170) * 100, 6);
    expect(triangleOf(block, 4).notApplicable).toBeNull();
  });

  it('licencia o triângulo 5 na coleta que o triângulo 1 chama de falha térmica alta', () => {
    const block = collectionWith({ ch4: [3], c2h4: [15], c2h2: ['ND'], c2h6: [3] });

    expect(conclusionOf(block, 'Triângulo 1')?.code).toBe('T3');
    expect(triangleOf(block, 5).readings.map((reading) => reading.kind)).toEqual(['plotada']);
    expect(conclusionOf(block, 'Triângulo 5')?.code).toBe('T3');
    expect(conclusionOf(block, 'Triângulo 4')?.code).toBe(NOT_ASSESSED_LABEL);
  });

  // A série de gases é posicional: o resultado n é da coluna n. Gás com menos
  // resultados que colunas abre lacuna na última coleta, e não empurra o valor
  // de 2024 para debaixo da data de 2025.
  it('abre lacuna na última coleta, e não desloca valores, quando o gás vem mais curto', () => {
    const block = gasBlock(['2024-04-26', '2025-10-26'], {
      ...MEASURED_COLLECTION,
      ch4: [601],
      c2h4: [960, 15],
      c2h2: [1297, 26],
    });
    const readings = triangleOf(block, 1).readings;

    expect(readings.map((reading) => reading.kind)).toEqual(['plotada', 'incomputavel']);
    expect(readings.map((reading) => reading.date)).toEqual(['2024-04-26', '2025-10-26']);
    if (readings[1].kind !== 'incomputavel') return;
    expect(readings[1].missingGas).toBe('CH₄');
  });
});

describe('DissolvedGasDiagnosisService, pentágono de Duval', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('posiciona a coleta e lista os cinco percentuais que a figura lê', () => {
    const pentagon = diagnosisOf(collectionWith({})).pentagon;
    const total = 2028 + 74 + 601 + 960 + 1297;

    expect(pentagon.point).not.toBeNull();
    expect(pentagon.percentages.map((item) => item.gas)).toEqual([
      'H₂',
      'C₂H₆',
      'CH₄',
      'C₂H₄',
      'C₂H₂',
    ]);
    expect(pentagon.percentages[0].percentage).toBeCloseTo((2028 / total) * 100, 6);
    expect(pentagon.notPlotted).toBeNull();
  });

  it('não atribui zona, e diz que a tabela de zonas não está no projeto', () => {
    const pentagon = diagnosisOf(collectionWith({})).pentagon;

    expect(pentagon.zone).toBeNull();
    expect(pentagon.zoneNotAssessed).toContain('zonas do pentágono');
    expect(conclusionOf(collectionWith({}), 'Pentágono de Duval')?.code).toBe(NOT_ASSESSED_LABEL);
  });

  it('não posiciona a coleta sem ensaio de um dos cinco gases, e diz qual faltou', () => {
    const pentagon = diagnosisOf(collectionWith({ c2h6: [null] })).pentagon;

    expect(pentagon.point).toBeNull();
    expect(pentagon.notPlotted).toBe('Sem ensaio de C₂H₆');
    expect(pentagon.percentages.every((item) => item.percentage === null)).toBeTrue();
  });
});

describe('DissolvedGasDiagnosisService, IEEE C57.104', () => {
  afterEach(() => TestBed.resetTestingModule());

  // A soma dos seis gases combustíveis deve coincidir com o totalizador do laudo.
  // A conferência pelo valor evita depender do rótulo usado na fonte.
  it('soma os seis gases combustíveis e bate com o totalizador do laudo', () => {
    const [reading] = diagnosisOf(collectionWith({})).ieee.readings;

    expect(reading.total).toBe(DEMONSTRATION_COMBUSTIBLE_TOTAL);
    expect(reading.notComputed).toBeNull();
  });

  it('não soma oxigênio nem nitrogênio, que não são combustíveis', () => {
    const [reading] = diagnosisOf(collectionWith({ o2: [0], n2: [0] })).ieee.readings;

    expect(reading.total).toBe(DEMONSTRATION_COMBUSTIBLE_TOTAL);
  });

  it('não soma a coleta sem ensaio de um combustível, e diz qual faltou', () => {
    const [reading] = diagnosisOf(collectionWith({ co: [null] })).ieee.readings;

    expect(reading.total).toBeNull();
    expect(reading.notComputed).toBe('Sem ensaio de CO');
  });

  it('não publica limiar de atenção nem de alarme, e diz por quê', () => {
    const ieee = diagnosisOf(collectionWith({})).ieee;

    expect(ieee.alarm).toBeNull();
    expect(ieee.attention).toBeNull();
    expect(ieee.thresholdsNotAssessed).toContain('não estão no projeto');
    expect(conclusionOf(collectionWith({}), 'IEEE C57.104')?.code).toBe(NOT_ASSESSED_LABEL);
  });

  it('mantém uma leitura por coleta, na ordem das colunas do laudo', () => {
    const block = gasBlock(['2024-04-26', '2025-10-26'], {
      ...MEASURED_COLLECTION,
      h2: [2028, 12],
      ch4: [601, 3],
      c2h6: [74, 3],
      c2h4: [960, 75],
      c2h2: [1297, 'ND'],
      co: [1239, 730],
    });
    const readings = diagnosisOf(block).ieee.readings;

    expect(readings.map((reading) => reading.date)).toEqual(['2024-04-26', '2025-10-26']);
    expect(readings.map((reading) => reading.total)).toEqual([DEMONSTRATION_COMBUSTIBLE_TOTAL, 823]);
  });
});

describe('DissolvedGasDiagnosisService, NBR 7274 e IEC 60599', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('mostra a concentração medida de cada gás de falha, sem oxigênio nem nitrogênio', () => {
    const nbr7274 = diagnosisOf(collectionWith({})).nbr7274;

    expect(nbr7274.readings.map((reading) => reading.gas)).toEqual([
      'H₂',
      'CH₄',
      'C₂H₆',
      'C₂H₄',
      'C₂H₂',
      'CO',
      'CO₂',
    ]);
    expect(nbr7274.readings.map((reading) => reading.value)).toEqual([
      2028, 601, 74, 960, 1297, 1239, 1228,
    ]);
  });

  it('não publica valor-limite por gás, e diz por quê', () => {
    const nbr7274 = diagnosisOf(collectionWith({})).nbr7274;

    expect(nbr7274.readings.every((reading) => reading.limit === null)).toBeTrue();
    expect(nbr7274.limitsNotAssessed).toContain('não está no projeto');
  });

  it('deixa lacuna no gás sem ensaio, em vez de desenhar barra zerada', () => {
    const nbr7274 = diagnosisOf(collectionWith({ co2: [null] })).nbr7274;
    const carbonDioxide = nbr7274.readings.find((reading) => reading.gas === 'CO₂');

    expect(carbonDioxide?.value).toBeNull();
  });

  it('calcula as três razões características da coleta mais recente', () => {
    const iec = diagnosisOf(collectionWith({})).iec60599;

    expect(iec.ratios.map((ratio) => ratio.label)).toEqual([
      'C₂H₂ / C₂H₄',
      'CH₄ / H₂',
      'C₂H₄ / C₂H₆',
    ]);
    expect(computedRatio(iec, 0)).toBeCloseTo(1297 / 960, 9);
    expect(computedRatio(iec, 1)).toBeCloseTo(601 / 2028, 9);
    expect(computedRatio(iec, 2)).toBeCloseTo(960 / 74, 9);
  });

  it('não atribui código de falha, e diz que a tabela de faixas não está no projeto', () => {
    const iec = diagnosisOf(collectionWith({})).iec60599;

    expect(iec.faultCode).toBeNull();
    expect(iec.faultCodeNotAssessed).toContain('Tabela 1 da IEC 60599');
    expect(conclusionOf(collectionWith({}), 'NBR 7274 / IEC 60599')?.code).toBe(NOT_ASSESSED_LABEL);
  });

  it('não devolve razão quando o gás do denominador não foi detectado', () => {
    const iec = diagnosisOf(collectionWith({ c2h4: ['ND'] })).iec60599;

    expect(iec.ratios[0].kind).toBe('denominatorNotDetected');
    if (iec.ratios[0].kind !== 'denominatorNotDetected') return;
    expect(iec.ratios[0].denominator).toBe('C₂H₄');
  });

  it('não devolve razão quando falta ensaio de um dos dois gases', () => {
    const iec = diagnosisOf(collectionWith({ h2: [null] })).iec60599;

    expect(iec.ratios[1].kind).toBe('withoutTest');
    if (iec.ratios[1].kind !== 'withoutTest') return;
    expect(iec.ratios[1].missingGas).toBe('H₂');
  });

  it('legenda os seis códigos de falha que a norma define, sem o DT do triângulo', () => {
    const codes = diagnosisOf(collectionWith({})).iecCodes.map((item) => item.code);

    expect(codes).toEqual(['PD', 'T1', 'T2', 'T3', 'D1', 'D2']);
  });
});

describe('DissolvedGasDiagnosisService, Rogers', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('calcula as quatro razões do método na coleta mais recente', () => {
    const rogers = diagnosisOf(collectionWith({})).rogers;

    expect(rogers.ratios.map((ratio) => ratio.label)).toEqual([
      'CH₄ / H₂',
      'C₂H₆ / CH₄',
      'C₂H₄ / C₂H₆',
      'C₂H₂ / C₂H₄',
    ]);
    expect(computedRatio(rogers, 0)).toBeCloseTo(601 / 2028, 9);
    expect(computedRatio(rogers, 1)).toBeCloseTo(74 / 601, 9);
    expect(computedRatio(rogers, 3)).toBeCloseTo(1297 / 960, 9);
  });

  it('não atribui código de falha, e diz que a tabela de faixas não está no projeto', () => {
    const rogers = diagnosisOf(collectionWith({})).rogers;

    expect(rogers.faultCode).toBeNull();
    expect(rogers.faultCodeNotAssessed).toContain('não está');
    expect(conclusionOf(collectionWith({}), 'Rogers')?.code).toBe(NOT_ASSESSED_LABEL);
  });
});

describe('DissolvedGasDiagnosisService, laudo sem gás dissolvido', () => {
  afterEach(() => TestBed.resetTestingModule());

  const service = () => TestBed.inject(DissolvedGasDiagnosisService);

  it('não diagnostica laudo de área sem cromatografia', () => {
    const block = gasBlock([], {});

    expect(service().diagnose(block, MINERAL)).toBeNull();
  });

  // Totalizador não é gás medido e se reconhece no fio pela fórmula vazia:
  // diagnosticar a partir dele seria diagnosticar a partir de uma soma.
  it('não diagnostica laudo que só publica totalizador', () => {
    const block: DissolvedGasBlock = {
      ...gasBlock(['2025-10-26'], {}),
      gases: [
        { key: 'combustiveis', name: 'Total de Gases Combustíveis', formula: '', results: [6199] },
        { key: 'total', name: 'Total de Gases', formula: '', results: [56134] },
      ],
    };

    expect(service().diagnose(block, MINERAL)).toBeNull();
  });
});

describe('DissolvedGasDiagnosisService, conclusões', () => {
  afterEach(() => TestBed.resetTestingModule());

  // Um método que não concluiu continua na lista, dizendo que não concluiu:
  // sumir com a linha faz a tela esconder que o método existe.
  it('fecha com uma conclusão para cada um dos sete métodos', () => {
    const methods = diagnosisOf(collectionWith({})).conclusions.map(
      (conclusion) => conclusion.method,
    );

    expect(methods).toEqual([
      'Triângulo 1',
      'Triângulo 4',
      'Triângulo 5',
      'Pentágono de Duval',
      'NBR 7274 / IEC 60599',
      'Rogers',
      'IEEE C57.104',
    ]);
  });

  it('descreve o veredito do triângulo 1 com o texto da tabela normativa', () => {
    const conclusion = conclusionOf(collectionWith({}), 'Triângulo 1');

    expect(conclusion?.code).toBe('D2');
    expect(conclusion?.description).toBe('Descargas de alta energia');
  });
});

describe('DissolvedGasDiagnosisService, massa de demonstração', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('aplica os métodos à massa de demonstração e marca que os gases estão abaixo dos valores típicos', () => {
    const outcome = outcomeOf(HEALTH_INDICATORS.dissolvedGases, HEALTH_INDICATORS.oilType);

    expect(HEALTH_INDICATORS.diagnosis).toBeNull();
    expect(outcome.kind === 'applied' && outcome.belowTypicalValues).toBeTrue();
  });
});

const REAL_CASE_DATES = ['2024-02-05', '2024-08-12', '2025-02-17', '2025-08-25'];

const REAL_CASE: ResultsByGas = {
  h2: [20, 8, 31, 36],
  o2: [7168, 23262, 19703, 22513],
  n2: [20799, 73699, 61548, 59148],
  ch4: [1, 1, 1, 1],
  co: [42, 61, 72, 88],
  co2: [257, 327, 305, 356],
  c2h4: ['ND', 'ND', 'ND', 'ND'],
  c2h6: ['ND', 'ND', 'ND', 'ND'],
  c2h2: ['ND', 'ND', 'ND', 'ND'],
};

describe('DissolvedGasDiagnosisService, gases abaixo dos valores típicos da IEC 60599', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('no caso real de quatro coletas aplica os sete métodos e marca os gases abaixo dos valores típicos', () => {
    const outcome = outcomeOf(gasBlock(REAL_CASE_DATES, REAL_CASE));

    expect(outcome.kind).toBe('applied');
    if (outcome.kind !== 'applied') return;
    expect(outcome.belowTypicalValues).toBeTrue();
    expect(outcome.conclusions.map((conclusion) => conclusion.method)).toEqual(DIAGNOSIS_METHODS);
  });

  it('CO e CO₂ altos sozinhos não tiram os gases de falha de abaixo dos valores típicos', () => {
    const outcome = outcomeOf(gasBlock(REAL_CASE_DATES, { ...REAL_CASE, co: [42, 61, 72, 900], co2: [257, 327, 305, 15000] }));

    expect(outcome.kind === 'applied' && outcome.belowTypicalValues).toBeTrue();
  });

  it('um gás de falha acima do valor típico na coleta mais recente tira a marca', () => {
    const outcome = outcomeOf(
      gasBlock(REAL_CASE_DATES, { ...REAL_CASE, h2: [20, 8, 31, 180], c2h2: ['ND', 'ND', 'ND', 5] }),
    );

    expect(outcome.kind === 'applied' && outcome.belowTypicalValues).toBeFalse();
  });

  it('gás sem ensaio na coleta mais recente não permite afirmar que os gases estão abaixo dos típicos', () => {
    const outcome = outcomeOf(
      gasBlock(REAL_CASE_DATES, { ...REAL_CASE, c2h2: ['ND', 'ND', 'ND', null] }),
    );

    expect(outcome.kind === 'applied' && outcome.belowTypicalValues).toBeFalse();
  });
});

describe('DissolvedGasDiagnosisService, tipo do óleo', () => {
  afterEach(() => TestBed.resetTestingModule());

  const faultyCollection = () => collectionWith({});

  it('aplica os métodos de óleo mineral ao óleo mineral', () => {
    expect(outcomeOf(faultyCollection(), MINERAL).kind).toBe('applied');
  });

  for (const oil of [
    { kind: 'natural-ester', reported: 'Óleo Vegetal Isolante' },
    { kind: 'silicone', reported: 'Silicone' },
  ] as const) {
    it(`não aplica Duval nem pentágono a ${oil.reported}`, () => {
      const outcome = outcomeOf(faultyCollection(), oil);

      expect(outcome.kind).toBe('withheld');
      if (outcome.kind !== 'withheld') return;
      expect(outcome.reason).toBe('oil-type-not-applicable');
      expect(outcome.message).toBe(METHOD_NOT_APPLICABLE_TO_OIL);
      expect(outcome.message).toBe('Método não aplicável a este tipo de óleo');
    });
  }

  it('tipo do óleo não informado não é tratado como mineral', () => {
    const outcome = outcomeOf(faultyCollection(), { kind: 'not-informed', reported: null });

    expect(outcome.kind).toBe('withheld');
    expect(outcome.kind === 'withheld' && outcome.message).toBe(OIL_TYPE_NOT_INFORMED);
  });

  it('tipo do óleo não reconhecido diz o que o cadastro informou e também não assume mineral', () => {
    const outcome = outcomeOf(faultyCollection(), { kind: 'unrecognized', reported: 'Ascarel' });

    expect(outcome.kind).toBe('withheld');
    expect(outcome.kind === 'withheld' && outcome.reason).toBe('oil-type-unrecognized');
    expect(outcome.kind === 'withheld' && outcome.detail).toContain('Ascarel');
  });
});
