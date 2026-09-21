// Triângulo de Duval 4 — norma, classificação e geometria.
//
// PROVENIÊNCIA E GRAU DE CONFIANÇA (leia antes de mexer num número):
//
// Fonte citável: IEEE Std C57.104-2019, Anexo D, Tabela D.3 / Figura D.3.
// NÃO cite como "Duval (2008)": o artigo original (IEEE Electr. Insul. Mag.
// 24(6), p. 22-29) é pago e NÃO foi lido por ninguém desta auditoria. Há
// indício secundário de que a forma de 2008 difira da de 2019 (sem a reta
// %H₂ = 15, sem %C₂H₆ = 30, sem zona ND, e S/ND em %C₂H₆ = 44 em vez de 46),
// e isso permanece NÃO CONFIRMADO.
//
// Confiança nos números abaixo: ALTA, mas por convergência de fontes
// secundárias, não por leitura da tabela impressa. A Tabela D.3 não foi
// decodificada do PDF da norma (a D.4, do triângulo 5, foi). Convergem, com
// os mesmos valores: (a) as retas rotuladas na figura publicada
// (%H₂ = 9 e 15, %CH₄ = 2, 15 e 36, %C₂H₆ = 1, 24, 30 e 46), lidas na
// imagem; (b) quatro implementações independentes — insatiablycivil/
// C57104wMCM (R, que cita a Tabela D.3), ekah1500/DGA_Diagnostic (Python e
// MATLAB), ToniMellin/dga-diagtool e a calculadora TriboTech.
//
// DIVERGÊNCIA CONHECIDA E DECISÃO TOMADA — zona C: o autor da implementação
// em R anota que a Tabela D.3 impressa traria `%CH₄ >= 36 E %C₂H₆ >= 24` e
// que isso é erro de digitação da norma. Adotamos `%C₂H₆ < 24`, porque é o
// que a figura publicada desenha (a zona C fica do lado de BAIXO C₂H₆ da
// reta %C₂H₆ = 24) e é o que as quatro implementações fazem. Quem tiver
// acesso institucional ao PDF deve conferir esta linha da Tabela D.3.
//
// LACUNA ADMITIDA — a cúspide %CH₄ < 2: a Cláusula D.4 da norma cita um
// código "R" ("Faults R will appear at the very top of Triangle 4 (H2 only)")
// que a tabela de zonas NÃO carrega. Todas as implementações jogam essa
// fresta em S. Seguimos o consenso; um ponto de H₂ quase puro sai como S,
// não como PD nem como R.
//
// A TABELA DE DESIGUALDADES É A FONTE ÚNICA, como no triângulo 1: os
// polígonos são artefato derivado, só para desenho. Nada classifica por
// ponto-em-polígono — ray-casting é indefinido em cima da aresta e as
// amostras reais caem justamente perto das arestas.
//
// Convenção de eixos: a = %H₂ (vértice de cima), b = %CH₄ (direita),
// c = %C₂H₆ (base). Bate com a figura publicada.

import type { CodigoDeDuval1 } from './duval-1';
import type { PontoTernario, ZonaTernaria } from './health';

/** Os três gases que entram no triângulo 4, em ppm. */
export interface GasesDoDuval4 {
  readonly h2: number | null;
  readonly ch4: number | null;
  readonly c2h6: number | null;
}

export type CodigoDeDuval4 = 'PD' | 'S' | 'C' | 'O' | 'ND';

export const DESCRICAO_DE_DUVAL_4: Record<CodigoDeDuval4, string> = {
  PD: 'Descargas parciais',
  S: 'Gaseificação espúria do óleo (abaixo de 200 °C)',
  C: 'Aquecimento com possível carbonização do papel (acima de 300 °C)',
  O: 'Sobreaquecimento do óleo (abaixo de 250 °C)',
  ND: 'Não determinado',
};

/**
 * Regra de aplicabilidade, normativa. IEEE Std C57.104-2019, p. 66, nota sob
 * a Tabela D.4: "Triangle 4 should be used only in case of faults identified
 * first as faults PD, T1 or T2 in Triangle 1" e "Triangles 4 and 5 should
 * never be used for faults identified first with Triangle 1 as electrical
 * faults D1 or D2".
 *
 * O triângulo 4 é refinamento condicional, não uma segunda opinião: como a
 * geometria cobre o simplex inteiro, ele SEMPRE devolve uma zona, inclusive
 * para amostras em que a norma proíbe usá-lo. Por isso o portão vive aqui, e
 * não no bom senso de quem lê a tela.
 */
export function aplicaSeDuval4(codigoDuval1: CodigoDeDuval1 | null): boolean {
  return codigoDuval1 === 'PD' || codigoDuval1 === 'T1' || codigoDuval1 === 'T2';
}

/** Texto que a tela mostra quando o triângulo 1 não licencia o 4. */
export const MOTIVO_DUVAL_4_NAO_APLICAVEL =
  'O triângulo 4 só se aplica a falhas identificadas antes como PD, T1 ou T2 no triângulo 1';

/**
 * Converte ppm em percentual ternário. Devolve `null` quando a coleta não
 * permite o diagnóstico. `null` é "não medido", que NÃO é zero medido: zero é
 * ponto legítimo na aresta, ausência não é ponto nenhum. Tratar ausência como
 * zero transforma gás não medido em diagnóstico confiante.
 */
export function duval4(gases: GasesDoDuval4): PontoTernario | null {
  const { h2, ch4, c2h6 } = gases;
  if (h2 == null || ch4 == null || c2h6 == null) return null;
  const total = h2 + ch4 + c2h6;
  if (total <= 0) return null;
  return { a: (h2 / total) * 100, b: (ch4 / total) * 100, c: (c2h6 / total) * 100 };
}

/** Qual gás faltou, para a tela conseguir dizer por que não plotou. */
export function gasAusenteDoDuval4(gases: GasesDoDuval4): string | null {
  if (gases.h2 == null) return 'H₂';
  if (gases.ch4 == null) return 'CH₄';
  if (gases.c2h6 == null) return 'C₂H₆';
  return null;
}

/**
 * A tabela normativa. Ordem e sinais de desigualdade são normativos: não
 * troque `<` por `<=` sem refazer o teste de partição.
 */
export function classificarDuval4(ponto: PontoTernario): CodigoDeDuval4 {
  const { a, b, c } = ponto;
  if (c < 1 && b >= 2 && b < 15) return 'PD';
  if (a < 9 && c >= 30) return 'O';
  if (a >= 9 && c >= 46) return 'ND';
  if (c < 24 && b >= 36) return 'C';
  if (c >= 24 && c < 30 && a < 15) return 'C';
  return 'S';
}

const ponto = (a: number, b: number, c: number): PontoTernario => ({ a, b, c });

/**
 * Polígonos derivados da tabela acima, cobrindo o triângulo inteiro sem
 * buraco nem sobreposição — `duval-4.spec.ts` regenera esta lista a partir
 * das desigualdades e falha se alguém editar um vértice na mão.
 *
 * A versão anterior era desenhada "pelo layout da tela": tinha as retas
 * %H₂ = 36 e %H₂ = 85 e mais três oblíquas, nenhuma delas normativa, e dava
 * diagnóstico diferente do publicado em 68,0% da área do triângulo (com S e
 * ND praticamente trocadas de lugar).
 */
export const ZONAS_DUVAL_4: readonly ZonaTernaria[] = [
  {
    codigo: 'PD',
    destacada: false,
    vertices: [ponto(98, 2, 0), ponto(85, 15, 0), ponto(84, 15, 1), ponto(97, 2, 1)],
  },
  {
    codigo: 'S',
    destacada: false,
    vertices: [
      ponto(100, 0, 0),
      ponto(98, 2, 0),
      ponto(97, 2, 1),
      ponto(84, 15, 1),
      ponto(85, 15, 0),
      ponto(64, 36, 0),
      ponto(40, 36, 24),
      ponto(15, 61, 24),
      ponto(15, 55, 30),
      ponto(9, 61, 30),
      ponto(9, 45, 46),
      ponto(54, 0, 46),
    ],
  },
  {
    codigo: 'C',
    destacada: false,
    vertices: [
      ponto(64, 36, 0),
      ponto(0, 100, 0),
      ponto(0, 70, 30),
      ponto(15, 55, 30),
      ponto(15, 61, 24),
      ponto(40, 36, 24),
    ],
  },
  {
    codigo: 'O',
    destacada: false,
    vertices: [ponto(0, 0, 100), ponto(9, 0, 91), ponto(9, 61, 30), ponto(0, 70, 30)],
  },
  {
    codigo: 'ND',
    destacada: false,
    vertices: [ponto(54, 0, 46), ponto(9, 45, 46), ponto(9, 0, 91)],
  },
];

/** Marca como destacada toda zona em que caiu pelo menos uma leitura. */
export function zonasDuval4Com(codigos: readonly CodigoDeDuval4[]): readonly ZonaTernaria[] {
  const acesas = new Set<string>(codigos);
  return ZONAS_DUVAL_4.map((zona) => ({ ...zona, destacada: acesas.has(zona.codigo) }));
}
