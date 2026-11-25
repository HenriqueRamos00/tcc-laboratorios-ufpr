import { MoedaPipe } from './moeda.pipe';

describe('MoedaPipe', () => {
  const pipe = new MoedaPipe();

  it('escreve o valor em reais com milhar e centavos', () => {
    expect(pipe.transform(3450).replace(/\s/g, ' ')).toBe('R$ 3.450,00');
  });

  it('ausência ou número inválido vira texto vazio', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(Number.NaN)).toBe('');
  });
});
