// O que estes casos prendem é a geometria, que é a única coisa que este módulo
// afirma.
//
// A orientação dos eixos entra aqui porque é a parte da geometria que nem o
// compilador nem a soma dos percentuais enxergam: cinco frações iguais caem na
// origem com qualquer permutação dos cinco ângulos, então só uma coleta
// dominada por um gás diz de que lado aquele eixo está.

import {
  missingPentagonGas,
  pentagonCentroid,
  pentagonPercentages,
  PENTAGON_AXES,
  type PentagonGases,
} from './duval-pentagon-1';

const EQUAL_SHARES: PentagonGases = { h2: 10, c2h6: 10, ch4: 10, c2h4: 10, c2h2: 10 };

describe('Pentágono de Duval, percentuais', () => {
  it('distribui os cinco gases sobre a soma dos cinco', () => {
    const percentages = pentagonPercentages({ h2: 50, c2h6: 25, ch4: 15, c2h4: 10, c2h2: 0 });

    expect(percentages.map((item) => item.gas)).toEqual(['H₂', 'C₂H₆', 'CH₄', 'C₂H₄', 'C₂H₂']);
    expect(percentages.map((item) => item.percentage)).toEqual([50, 25, 15, 10, 0]);
  });

  it('não distribui percentual nenhum quando falta ensaio de um dos cinco', () => {
    const gases: PentagonGases = { h2: 50, c2h6: 25, ch4: 15, c2h4: 10, c2h2: null };

    expect(pentagonPercentages(gases).every((item) => item.percentage === null)).toBeTrue();
    expect(missingPentagonGas(gases)).toBe('C₂H₂');
  });

  it('não distribui percentual nenhum quando nada foi detectado', () => {
    const percentages = pentagonPercentages({ h2: 0, c2h6: 0, ch4: 0, c2h4: 0, c2h2: 0 });

    expect(percentages.every((item) => item.percentage === null)).toBeTrue();
  });
});

describe('Pentágono de Duval, centroide', () => {
  it('leva ao centro a coleta com os cinco gases em frações iguais', () => {
    const point = pentagonCentroid(EQUAL_SHARES);

    expect(point).not.toBeNull();
    expect(point?.x).toBeCloseTo(0, 6);
    expect(point?.y).toBeCloseTo(0, 6);
  });

  // y negativo é para cima no eixo da tela, que é o contrato do componente de
  // desenho: trocar o sinal joga toda leitura para o lado oposto da figura.
  it('puxa para o topo a coleta dominada por hidrogênio', () => {
    const point = pentagonCentroid({ h2: 900, c2h6: 25, ch4: 25, c2h4: 25, c2h2: 25 });

    expect(point).not.toBeNull();
    expect(point?.y).toBeLessThan(0);
    expect(Math.abs(point?.x ?? 1)).toBeLessThan(0.05);
  });

  it('puxa para a esquerda a coleta dominada por acetileno', () => {
    expect(pentagonCentroid({ h2: 25, c2h6: 25, ch4: 25, c2h4: 25, c2h2: 900 })?.x).toBeLessThan(0);
  });

  it('puxa para a direita a coleta dominada por etano', () => {
    expect(
      pentagonCentroid({ h2: 25, c2h6: 900, ch4: 25, c2h4: 25, c2h2: 25 })?.x,
    ).toBeGreaterThan(0);
  });

  it('não plota a coleta sem ensaio de um dos cinco gases', () => {
    expect(pentagonCentroid({ h2: 50, c2h6: 25, ch4: 15, c2h4: 10, c2h2: null })).toBeNull();
  });

  it('não plota a coleta em que nada foi detectado', () => {
    expect(pentagonCentroid({ h2: 0, c2h6: 0, ch4: 0, c2h4: 0, c2h2: 0 })).toBeNull();
  });

  // Com um gás só, os outros quatro vértices caem na origem: a figura não tem
  // área e o centroide dividiria por zero.
  it('não plota a coleta com um gás detectado só', () => {
    expect(pentagonCentroid({ h2: 120, c2h6: 0, ch4: 0, c2h4: 0, c2h2: 0 })).toBeNull();
  });
});

describe('Pentágono de Duval, eixos', () => {
  // Pentágono regular: cinco eixos, 72° entre vizinhos. O resto a 360 existe
  // porque a volta fecha do último eixo de volta no primeiro.
  it('mantém os cinco eixos a 72° um do outro', () => {
    const angles = PENTAGON_AXES.map((axis) => axis.angleInDegrees);
    const differences = angles.map(
      (angle, index) => (((angle - angles[(index + 1) % angles.length]) % 360) + 360) % 360,
    );

    expect(angles.length).toBe(5);
    expect(differences).toEqual([72, 72, 72, 72, 72]);
  });
});
