import { classificarDuval1, zonasDuval1Com, type CodigoDeDuval1 } from './duval-1';
import { classificarDuval4 } from './duval-4';
import { classificarDuval5 } from './duval-5';

// Valor-limite nas fronteiras das tabelas normativas, nas retas que os outros
// specs dos tres triangulos ainda nao visitam. Cada caso afirma o ponto EM CIMA
// da reta e o ponto imediatamente abaixo dela, que e o par que distingue `<` de
// `<=`: so o valor de dentro nao distingue, porque ele continua do mesmo lado
// nas duas formas da desigualdade.
//
// A amostra real nao visita nenhuma destas retas: os quatro protocolos medidos
// dao T1, T2, T3 e um incomputavel, todos longe de fronteira. Por isso a
// fronteira entra por valor-limite, e o dado medido entra em
// `diagnosis-measured-sample.spec.ts`.
//
// Convencao de eixos, igual a dos modulos:
//   triangulo 1  a = %CH4,  b = %C2H4, c = %C2H2
//   triangulo 4  a = %H2,   b = %CH4,  c = %C2H6
//   triangulo 5  a = %CH4,  b = %C2H4, c = %C2H6

/** As sete zonas do triangulo 1, para a particao de colecao. */
const ALL_DUVAL_1_CODES: readonly CodigoDeDuval1[] = ['PD', 'T1', 'T2', 'T3', 'D1', 'D2', 'DT'];

function highlightedCodesOf(codes: readonly CodigoDeDuval1[]): readonly string[] {
  return zonasDuval1Com(codes)
    .filter((zone) => zone.highlighted)
    .map((zone) => zone.code);
}

describe('Fronteiras normativas do triangulo 1', () => {
  it('decide mistura de falhas em cima da reta de 4 por cento de acetileno, e termica abaixo', () => {
    expect(classificarDuval1({ a: 76, b: 20, c: 4 })).toBe('DT');
    expect(classificarDuval1({ a: 76.1, b: 20, c: 3.9 })).toBe('T2');
  });

  it('decide acima de 300 graus em cima da reta de 20 por cento de etileno, e abaixo de 300 sob ela', () => {
    expect(classificarDuval1({ a: 80, b: 20, c: 0 })).toBe('T2');
    expect(classificarDuval1({ a: 80.1, b: 19.9, c: 0 })).toBe('T1');
  });

  it('decide acima de 700 graus em cima da reta de 50 por cento de etileno, e abaixo de 700 sob ela', () => {
    expect(classificarDuval1({ a: 50, b: 50, c: 0 })).toBe('T3');
    expect(classificarDuval1({ a: 50.1, b: 49.9, c: 0 })).toBe('T2');
  });

  it('decide mistura de falhas em cima da reta de 15 por cento de acetileno, com etileno alto', () => {
    expect(classificarDuval1({ a: 35, b: 50, c: 15 })).toBe('DT');
    expect(classificarDuval1({ a: 35.1, b: 50, c: 14.9 })).toBe('T3');
  });

  it('decide descarga de baixa energia em cima da reta de 13 por cento de acetileno', () => {
    expect(classificarDuval1({ a: 87, b: 0, c: 13 })).toBe('D1');
    expect(classificarDuval1({ a: 87.1, b: 0, c: 12.9 })).toBe('DT');
  });

  it('decide descarga de alta energia em cima da reta de 23 por cento de etileno', () => {
    expect(classificarDuval1({ a: 64, b: 23, c: 13 })).toBe('D2');
    expect(classificarDuval1({ a: 64.1, b: 22.9, c: 13 })).toBe('D1');
  });

  it('decide descarga de alta energia em cima da reta de 29 por cento de acetileno, com etileno alto', () => {
    expect(classificarDuval1({ a: 31, b: 40, c: 29 })).toBe('D2');
    expect(classificarDuval1({ a: 31.1, b: 40, c: 28.9 })).toBe('DT');
  });

  it('acende zona nenhuma sem leitura, uma com uma, duas com duas e as sete com as sete', () => {
    expect(highlightedCodesOf([])).toEqual([]);
    expect(highlightedCodesOf(['T2'])).toEqual(['T2']);
    expect(highlightedCodesOf(['T1', 'T2'])).toEqual(['T1', 'T2']);
    expect(highlightedCodesOf(ALL_DUVAL_1_CODES).length).toBe(ALL_DUVAL_1_CODES.length);
  });
});

describe('Fronteiras normativas do triangulo 4', () => {
  it('deixa de ver descarga parcial em cima da reta de 1 por cento de etano', () => {
    expect(classificarDuval4({ a: 94, b: 5, c: 1 })).toBe('S');
    expect(classificarDuval4({ a: 94.1, b: 5, c: 0.9 })).toBe('PD');
  });

  it('decide sobreaquecimento do oleo em cima da reta de 30 por cento de etano, com hidrogenio baixo', () => {
    expect(classificarDuval4({ a: 8, b: 62, c: 30 })).toBe('O');
    expect(classificarDuval4({ a: 8, b: 62.1, c: 29.9 })).toBe('C');
  });
});

describe('Fronteiras normativas do triangulo 5', () => {
  it('decide descarga parcial em cima da reta de 2 por cento de etano, e sobreaquecimento abaixo', () => {
    expect(classificarDuval5({ a: 98, b: 0, c: 2 })).toBe('PD');
    expect(classificarDuval5({ a: 98.1, b: 0, c: 1.9 })).toBe('O');
  });

  it('decide falha nao determinada em cima da reta de 10 por cento de etileno, com etano alto', () => {
    expect(classificarDuval5({ a: 60, b: 10, c: 30 })).toBe('ND');
    expect(classificarDuval5({ a: 60.1, b: 9.9, c: 30 })).toBe('S');
  });
});
