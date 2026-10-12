import { eixoDeValores, rotuloDoEixo } from './eixo-de-valores';

// O eixo ingênuo fatiava a faixa medida em partes iguais e produzia 270,8 / 309,68 /
// 348,56 no IEEE e 576 / 1.151 / 1.727 na NBR: largas demais e, em português,
// confundidas com decimal por causa do ponto de milhar.
describe('eixoDeValores', () => {
  it('fecha a escala em passo redondo, acima do maior e abaixo do menor', () => {
    const eixo = eixoDeValores(287, 449);

    expect(eixo.marcas).toEqual([250, 300, 350, 400, 450]);
    expect(eixo.minimo).toBeLessThanOrEqual(287);
    expect(eixo.maximo).toBeGreaterThanOrEqual(449);
  });

  it('nunca deixa um valor medido fora do quadro', () => {
    for (const [menor, maior] of [
      [0, 2385],
      [1, 7],
      [48, 2385],
      [0.2, 0.9],
    ]) {
      const eixo = eixoDeValores(menor, maior);

      expect(eixo.minimo).withContext(`${menor}..${maior}`).toBeLessThanOrEqual(menor);
      expect(eixo.maximo).withContext(`${menor}..${maior}`).toBeGreaterThanOrEqual(maior);
    }
  });

  // Série de um valor só, e série sem valor nenhum, são casos reais da amostra: um
  // equipamento com uma coleta, e um laudo sem gás dissolvido.
  it('devolve escala utilizável sem faixa para calcular', () => {
    expect(eixoDeValores(0, 0).maximo).toBeGreaterThan(0);
    expect(eixoDeValores(449, 449).maximo).toBeGreaterThanOrEqual(449);
    expect(eixoDeValores(Number.NaN, Number.NaN).marcas.length).toBeGreaterThan(1);
  });
});

describe('rotuloDoEixo', () => {
  // `toLocaleString('pt-BR')` escreve 2879 como "2.879", que num eixo se lê como dois
  // inteiros e oitocentos. O separador aqui é espaço fino, que não tem essa leitura.
  it('separa o milhar sem usar o ponto', () => {
    expect(rotuloDoEixo(2385)).toBe('2 385');
    expect(rotuloDoEixo(449)).toBe('449');
    expect(rotuloDoEixo(0)).toBe('0');
  });
});
