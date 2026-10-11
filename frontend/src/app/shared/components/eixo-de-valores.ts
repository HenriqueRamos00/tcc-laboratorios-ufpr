// Passo "redondo" para o eixo de valores, no algoritmo que as bibliotecas de gráfico
// usam: a escala sobe em 1, 2 ou 5 vezes uma potência de dez.
//
// Fatiar a faixa medida em partes iguais, que é o caminho ingênuo, produz marca como
// 270,8 / 309,68 / 348,56. Além de ocupar largura demais, ela é ambígua em português:
// `toLocaleString('pt-BR')` escreve 2879 como "2.879", e quem lê o eixo vê dois
// inteiros e oitocentos.
const PASSOS_PREFERIDOS = [1, 2, 2.5, 5, 10];

export interface EixoDeValores {
  readonly minimo: number;
  readonly maximo: number;
  readonly marcas: readonly number[];
}

function passoRedondo(faixa: number, marcasDesejadas: number): number {
  const bruto = faixa / marcasDesejadas;
  const potencia = Math.pow(10, Math.floor(Math.log10(bruto)));

  return (PASSOS_PREFERIDOS.find((candidato) => candidato * potencia >= bruto) ?? 10) * potencia;
}

/**
 * Escolhe as bordas e as marcas do eixo para a faixa medida.
 *
 * As bordas caem no passo redondo imediatamente fora da faixa, nunca dentro: o topo
 * acima do maior valor e a base abaixo do menor, para nenhum ponto sair do quadro.
 *
 * O mínimo entra como parâmetro porque gráfico de linha e gráfico de barra pedem
 * coisas diferentes. Barra nasce do zero, senão a altura dela deixa de ser
 * proporcional ao valor e a figura exagera a diferença; linha de evolução pode começar
 * perto do menor valor, senão uma variação pequena vira uma reta achatada no topo.
 */
export function eixoDeValores(
  menorMedido: number,
  maiorMedido: number,
  marcasDesejadas = 5,
): EixoDeValores {
  if (!Number.isFinite(menorMedido) || !Number.isFinite(maiorMedido) || maiorMedido <= menorMedido) {
    const topo = Number.isFinite(maiorMedido) && maiorMedido > 0 ? maiorMedido : 1;

    return { minimo: 0, maximo: topo, marcas: [0, topo] };
  }

  const passo = passoRedondo(maiorMedido - menorMedido, marcasDesejadas);
  const minimo = Math.floor(menorMedido / passo) * passo;
  const maximo = Math.ceil(maiorMedido / passo) * passo;
  const marcas: number[] = [];

  for (let valor = minimo; valor <= maximo + passo / 2; valor += passo) {
    marcas.push(Number(valor.toFixed(10)));
  }

  return { minimo, maximo, marcas };
}

/**
 * Inteiro com separador de milhar que não se confunde com vírgula decimal. Em
 * português o separador é o ponto, e "2.879" num eixo se lê como dois inteiros e
 * oitocentos; o espaço fino separa sem criar essa dúvida.
 */
export function rotuloDoEixo(valor: number): string {
  const arredondado = Math.abs(valor) >= 10 ? Math.round(valor) : Number(valor.toFixed(2));

  return arredondado.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
}
