import { NumeroPipe } from './numero.pipe';

describe('NumeroPipe', () => {
  const pipe = new NumeroPipe();

  it('usa vírgula decimal e ponto de milhar', () => {
    expect(pipe.transform(2879.5)).toBe('2.879,5');
    expect(pipe.transform(12)).toBe('12');
  });

  it('limita as casas decimais quando pedido', () => {
    expect(pipe.transform(1.23456, 2)).toBe('1,23');
    expect(pipe.transform(45.678, 1)).toBe('45,7');
  });

  it('ausência vira texto vazio', () => {
    expect(pipe.transform(null)).toBe('');
  });
});
