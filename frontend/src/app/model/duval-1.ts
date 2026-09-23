// Triângulo de Duval 1 — norma, classificação e geometria.
//
// A TABELA DE DESIGUALDADES ABAIXO É A FONTE ÚNICA. Os polígonos de
// ZONAS_DUVAL_1 são artefato derivado dela, só para desenho: nada no
// aplicativo classifica por ponto-em-polígono. O motivo é que ray-casting é
// indefinido em cima da aresta, e as amostras reais caem justamente perto das
// arestas — medimos 199 divergências entre tabela e geometria, todas sobre
// fronteira e nenhuma no interior. Quem decide a falha é a tabela.
//
// As desigualdades são meia-abertas e avaliadas NESTA ORDEM: a primeira que
// casar vence. Sem a ordem, T1/T2 e D1/D2 se sobrepõem.
//
// Convenção de eixos desta casa para o triângulo 1 (igual à do protótipo da
// tela): a = %CH₄ (vértice de cima), b = %C₂H₄ (direita), c = %C₂H₂ (base).
// Atenção: a literatura costuma trocar b e c. Os rótulos de eixo em
// `TrianguloDeDuval` precisam continuar batendo com esta ordem.

import type { PontoTernario, ZonaTernaria } from './health';

/** Os três gases que entram no triângulo 1, em ppm. */
export interface GasesDoDuval1 {
  readonly ch4: number | null;
  readonly c2h4: number | null;
  readonly c2h2: number | null;
}

export type CodigoDeDuval1 = 'PD' | 'T1' | 'T2' | 'T3' | 'D1' | 'D2' | 'DT';

/** Faixa de temperatura, não nível de energia: T1/T2/T3 são térmicas. */
export const DESCRICAO_DE_DUVAL_1: Record<CodigoDeDuval1, string> = {
  PD: 'Descargas parciais',
  T1: 'Falha térmica abaixo de 300 °C',
  T2: 'Falha térmica entre 300 °C e 700 °C',
  T3: 'Falha térmica acima de 700 °C',
  D1: 'Descargas de baixa energia',
  D2: 'Descargas de alta energia',
  DT: 'Mistura de falhas térmicas e elétricas',
};

/**
 * Converte ppm em percentual ternário. Devolve `null` quando a coleta não
 * permite o diagnóstico — na prática, quando o laboratório não reportou o
 * C₂H₂. `null` aqui é "não medido", que é diferente de zero medido: zero é um
 * ponto legítimo na base do triângulo, ausência não é ponto nenhum.
 */
export function duval1(gases: GasesDoDuval1): PontoTernario | null {
  const { ch4, c2h4, c2h2 } = gases;
  if (ch4 == null || c2h4 == null || c2h2 == null) return null;
  const total = ch4 + c2h4 + c2h2;
  if (total <= 0) return null;
  return { a: (ch4 / total) * 100, b: (c2h4 / total) * 100, c: (c2h2 / total) * 100 };
}

/** Qual gás faltou, para a tela conseguir dizer por que não plotou. */
export function gasAusenteDoDuval1(gases: GasesDoDuval1): string | null {
  if (gases.ch4 == null) return 'CH₄';
  if (gases.c2h4 == null) return 'C₂H₄';
  if (gases.c2h2 == null) return 'C₂H₂';
  return null;
}

/**
 * A tabela normativa. Ordem e sinais de desigualdade são normativos: não
 * troque `<` por `<=` sem refazer o teste de partição.
 */
export function classificarDuval1(ponto: PontoTernario): CodigoDeDuval1 {
  const { a, b, c } = ponto;
  if (a >= 98) return 'PD';
  if (c < 4 && b < 20) return 'T1';
  if (c < 4 && b < 50) return 'T2';
  if (c < 15 && b >= 50) return 'T3';
  if (c >= 13 && b < 23) return 'D1';
  if ((c >= 13 && b >= 23 && b < 40) || (c >= 29 && b >= 40)) return 'D2';
  return 'DT';
}

const ponto = (a: number, b: number, c: number): PontoTernario => ({ a, b, c });

/**
 * Polígonos derivados da tabela acima, cobrindo o triângulo inteiro sem buraco
 * nem sobreposição — `duval-1.spec.ts` regenera esta lista a partir das
 * desigualdades e falha se alguém editar um vértice na mão.
 *
 * A versão anterior deixava 13,2% do triângulo sem zona, na cunha
 * %C₂H₄ > 50 ∧ %C₂H₂ > 15: um ponto ali não acendia zona nenhuma. D2 e DT
 * abaixo são as únicas que mudaram de forma; PD/T1/T2/T3/D1 saem idênticas.
 */
export const ZONAS_DUVAL_1: readonly ZonaTernaria[] = [
  { codigo: 'PD', destacada: false, vertices: [ponto(100, 0, 0), ponto(98, 2, 0), ponto(98, 0, 2)] },
  {
    codigo: 'T1',
    destacada: false,
    vertices: [ponto(98, 2, 0), ponto(80, 20, 0), ponto(76, 20, 4), ponto(96, 0, 4), ponto(98, 0, 2)],
  },
  {
    codigo: 'T2',
    destacada: false,
    vertices: [ponto(80, 20, 0), ponto(50, 50, 0), ponto(46, 50, 4), ponto(76, 20, 4)],
  },
  {
    codigo: 'T3',
    destacada: false,
    vertices: [ponto(50, 50, 0), ponto(0, 100, 0), ponto(0, 85, 15), ponto(35, 50, 15)],
  },
  {
    codigo: 'D1',
    destacada: false,
    vertices: [ponto(87, 0, 13), ponto(64, 23, 13), ponto(0, 23, 77), ponto(0, 0, 100)],
  },
  {
    codigo: 'D2',
    destacada: false,
    vertices: [ponto(64, 23, 13), ponto(47, 40, 13), ponto(31, 40, 29), ponto(0, 71, 29), ponto(0, 23, 77)],
  },
  {
    codigo: 'DT',
    destacada: false,
    vertices: [
      ponto(96, 0, 4),
      ponto(46, 50, 4),
      ponto(35, 50, 15),
      ponto(0, 85, 15),
      ponto(0, 71, 29),
      ponto(31, 40, 29),
      ponto(47, 40, 13),
      ponto(87, 0, 13),
    ],
  },
];

/** Marca como destacada toda zona em que caiu pelo menos uma leitura. */
export function zonasDuval1Com(codigos: readonly CodigoDeDuval1[]): readonly ZonaTernaria[] {
  const acesas = new Set<string>(codigos);
  return ZONAS_DUVAL_1.map((zona) => ({ ...zona, destacada: acesas.has(zona.codigo) }));
}
