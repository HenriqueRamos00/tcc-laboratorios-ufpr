import {
  calcularSubtotalEmCentavos,
  converterCentavosParaValorSeguro,
  converterValorParaCentavosSeguro,
} from './orcamento-money';

describe('Cálculo monetário de orçamentos', () => {
  it('converte centavos a partir da representação decimal sem perder 0,29', () => {
    expect(converterValorParaCentavosSeguro(0.1)).toBe(10);
    expect(converterValorParaCentavosSeguro(0.29)).toBe(29);
    expect(calcularSubtotalEmCentavos(3, 0.1)).toBe(30n);
    expect(calcularSubtotalEmCentavos(1, 0.29)).toBe(29n);
    expect(converterCentavosParaValorSeguro(30n)).toBe(0.3);
  });

  it('rejeita valores com fração menor que um centavo e valores fora do limite seguro', () => {
    expect(converterValorParaCentavosSeguro(0.001)).toBeNull();
    expect(converterValorParaCentavosSeguro(1e-9)).toBeNull();
    expect(converterValorParaCentavosSeguro(100_000_000_000_000)).toBeNull();
    expect(converterCentavosParaValorSeguro(BigInt(Number.MAX_SAFE_INTEGER) + 1n)).toBeNull();
  });
});
