export const LIMITE_AMOSTRAS_ORCAMENTO = 2147483647;
export const CENTAVOS_POR_REAL = 100;

const MAIOR_CENTAVO_SEGURO = BigInt(Number.MAX_SAFE_INTEGER);

export function converterValorParaCentavosSeguro(valor: number): number | null {
  if (!Number.isFinite(valor) || valor < 0) return null;
  const [mantissa, expoenteTexto] = valor.toString().toLowerCase().split('e');
  const [parteInteira, parteFracionaria = ''] = mantissa.split('.');
  const expoente = expoenteTexto ? Number(expoenteTexto) : 0;
  const digitos = `${parteInteira}${parteFracionaria}`;
  const posicaoDecimalCentavos = parteInteira.length + expoente + 2;

  let digitosCentavos: string;
  if (posicaoDecimalCentavos <= 0) {
    if (/[1-9]/.test(digitos)) return null;
    digitosCentavos = '0';
  } else if (posicaoDecimalCentavos >= digitos.length) {
    digitosCentavos = `${digitos}${'0'.repeat(posicaoDecimalCentavos - digitos.length)}`;
  } else {
    const restante = digitos.slice(posicaoDecimalCentavos);
    if (/[1-9]/.test(restante)) return null;
    digitosCentavos = digitos.slice(0, posicaoDecimalCentavos) || '0';
  }

  const centavos = BigInt(digitosCentavos);
  return centavos <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(centavos) : null;
}

export function calcularSubtotalEmCentavos(
  quantidade: number,
  precoUnitario: number,
): bigint | null {
  if (!Number.isInteger(quantidade) || quantidade < 1 || quantidade > LIMITE_AMOSTRAS_ORCAMENTO) {
    return null;
  }
  const centavosUnitarios = converterValorParaCentavosSeguro(precoUnitario);
  if (centavosUnitarios === null) return null;
  return BigInt(quantidade) * BigInt(centavosUnitarios);
}

export function converterCentavosParaValorSeguro(centavos: bigint): number | null {
  if (centavos < 0n || centavos > MAIOR_CENTAVO_SEGURO) return null;
  const valor = Number(centavos) / CENTAVOS_POR_REAL;
  const centavosConvertidos = converterValorParaCentavosSeguro(valor);
  return centavosConvertidos !== null && BigInt(centavosConvertidos) === centavos
    ? valor
    : null;
}
