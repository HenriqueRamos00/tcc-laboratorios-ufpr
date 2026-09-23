// Triângulo de Duval 5 — norma, classificação e geometria.
//
// PROVENIÊNCIA E GRAU DE CONFIANÇA (leia antes de mexer num número):
//
// Fonte citável: IEEE Std C57.104-2019, Anexo D, Tabela D.4 / Figura D.4
// (p. 66). Aqui a tabela impressa foi de fato DECODIFICADA do PDF tipografado
// da norma, célula a célula — confiança ALTA, maior que a do triângulo 4.
// NÃO cite como "Duval (2008)": o artigo original é pago e não foi lido.
//
// Preâmbulo da Tabela D.4, verbatim: "Numerical values for fault zone
// boundaries of Duval Triangle 5 method are the following, expressed in
// %CH4, %C2H4 and %C2H6". A coluna %CH₄ é vazia em todas as linhas: CH₄ é o
// vértice dependente (%CH₄ = 100 − %C₂H₄ − %C₂H₆). Valores distintos em toda
// a tabela: %C₂H₄ ∈ {1, 10, 35, 50, 70} e %C₂H₆ ∈ {2, 12, 14, 30, 54}.
//
// DUAS DIVERGÊNCIAS ENTRE FONTES, E O QUE ADOTAMOS:
//
// 1. %C₂H₆ = 14 vs 15 (fronteira PD/O-superior ↔ S). A Tabela D.4 diz 14, e
//    é o que ADOTAMOS, por ser a fonte que citamos. Metade das implementações
//    de mercado usa 15 e a figura redesenhada mais difundida rotula a reta
//    como "C2H6 = 15"; a alegação de que o Duval de 2008 traz 15 é
//    secundária e NÃO CONFIRMADA. A escolha muda o veredito em 0,200% da
//    área (a faixa 14 ≤ %C₂H₆ < 15 com %C₂H₄ < 10, onde decide PD/O vs S).
//
// 2. %C₂H₄ = 48 vs 50 (fronteira da zona C). A Tabela D.4 diz 50, e é o que
//    ADOTAMOS. O "48" vem de uma figura redesenhada; a tentativa de dirimir
//    medindo o pixel da reta foi inconclusiva (±1–2 pontos percentuais).
//
// LACUNA DA PRÓPRIA NORMA, FECHADA DE PROPÓSITO: a Tabela D.4 é disjunta mas
// NÃO é total. A fresta %C₂H₄ ≥ 1 e < 10 com %C₂H₆ < 2 não casa com nenhuma
// linha publicada — 0,36% da área fica sem zona. Nossa tabela a atribui a O,
// que é o que toda implementação faz. Isto é decisão nossa, não texto da
// norma, e está dito aqui para a banca não descobrir sozinha.
//
// NOMENCLATURA: os rótulos publicados são PD, O, S, T2, T3, C e ND. A versão
// anterior deste projeto usava "T2-H" e "T3-H". "T3-H" existe, mas é zona do
// PENTÁGONO 2 de Duval (falha térmica só no óleo, sem carbonização de papel);
// "T2-H" não existe em fonte nenhuma — em Cheim, Duval & Haider (Energies
// 2020, 13, 2859), com Duval como coautor, "T3-H" aparece 18 vezes e "T2-H"
// zero, porque falhas T2 quase sempre envolvem carbonização de papel.
//
// ATENÇÃO: o triângulo 5 é o de ALTA temperatura. A Figura D.4 se chama
// "Duval Triangle 5 method for high temperature fault". Várias ferramentas o
// rotulam como "baixa temperatura" por contaminação do título do artigo de
// 2008, que se refere ao triângulo 4.
//
// A TABELA DE DESIGUALDADES É A FONTE ÚNICA; os polígonos são artefato
// derivado, só para desenho, como no triângulo 1.
//
// Convenção de eixos: a = %CH₄ (vértice de cima), b = %C₂H₄ (direita),
// c = %C₂H₆ (base). Bate com a figura publicada.

import type { CodigoDeDuval1 } from './duval-1';
import type { PontoTernario, ZonaTernaria } from './health';

/** Os três gases que entram no triângulo 5, em ppm. */
export interface GasesDoDuval5 {
  readonly ch4: number | null;
  readonly c2h4: number | null;
  readonly c2h6: number | null;
}

export type CodigoDeDuval5 = 'PD' | 'S' | 'C' | 'O' | 'T2' | 'T3' | 'ND';

export const DESCRICAO_DE_DUVAL_5: Record<CodigoDeDuval5, string> = {
  PD: 'Descargas parciais',
  S: 'Gaseificação espúria do óleo (abaixo de 200 °C)',
  // "Possível" é da norma: Cl. D.4 diz "possible carbonization of paper".
  // Confirme com CO/CO₂ e furanos antes de escalar.
  C: 'Ponto quente com possível carbonização do papel (acima de 300 °C)',
  O: 'Sobreaquecimento do óleo (abaixo de 250 °C)',
  T2: 'Falha térmica entre 300 °C e 700 °C, só no óleo',
  T3: 'Falha térmica acima de 700 °C, só no óleo',
  ND: 'Não determinado',
};

/**
 * Regra de aplicabilidade, normativa. IEEE Std C57.104-2019, p. 66, nota sob
 * a Tabela D.4: "Triangle 5 should be used only in case of faults identified
 * first as faults T2 or T3 in Triangle 1" e "Triangles 4 and 5 should never
 * be used for faults identified first with Triangle 1 as electrical faults
 * D1 or D2".
 *
 * Como a geometria cobre o simplex inteiro, o triângulo 5 SEMPRE devolve uma
 * zona — inclusive para amostras em que a norma proíbe usá-lo. Desenhar os
 * três triângulos incondicionalmente fabricava veredito com cara de confiante
 * para toda coleta; o portão existe para isso não acontecer.
 */
export function aplicaSeDuval5(codigoDuval1: CodigoDeDuval1 | null): boolean {
  return codigoDuval1 === 'T2' || codigoDuval1 === 'T3';
}

/** Texto que a tela mostra quando o triângulo 1 não licencia o 5. */
export const MOTIVO_DUVAL_5_NAO_APLICAVEL =
  'O triângulo 5 só se aplica a falhas identificadas antes como T2 ou T3 no triângulo 1';

/**
 * Converte ppm em percentual ternário. `null` quando a coleta não permite o
 * diagnóstico — ausência de gás NÃO é zero medido.
 */
export function duval5(gases: GasesDoDuval5): PontoTernario | null {
  const { ch4, c2h4, c2h6 } = gases;
  if (ch4 == null || c2h4 == null || c2h6 == null) return null;
  const total = ch4 + c2h4 + c2h6;
  if (total <= 0) return null;
  return { a: (ch4 / total) * 100, b: (c2h4 / total) * 100, c: (c2h6 / total) * 100 };
}

/** Qual gás faltou, para a tela conseguir dizer por que não plotou. */
export function gasAusenteDoDuval5(gases: GasesDoDuval5): string | null {
  if (gases.ch4 == null) return 'CH₄';
  if (gases.c2h4 == null) return 'C₂H₄';
  if (gases.c2h6 == null) return 'C₂H₆';
  return null;
}

/**
 * A tabela normativa, transcrita da Tabela D.4. Ordem e sinais de
 * desigualdade são normativos: não troque `<` por `<=` sem refazer o teste de
 * partição.
 *
 * A terceira linha (`b < 10` sem condição em c) é a que fecha a fresta que a
 * norma deixou sem zona — ver o cabeçalho deste arquivo.
 */
export function classificarDuval5(ponto: PontoTernario): CodigoDeDuval5 {
  const { b, c } = ponto;
  if (b < 1 && c >= 2 && c < 14) return 'PD';
  if (b < 10 && c >= 14 && c < 54) return 'S';
  if (b < 10) return 'O';
  if (b < 35 && c >= 30) return 'ND';
  if (b < 35 && c < 12) return 'T2';
  if (b < 50 && c >= 12 && c < 30) return 'C';
  if (b < 70 && c >= 14 && c < 30) return 'C';
  return 'T3';
}

const ponto = (a: number, b: number, c: number): PontoTernario => ({ a, b, c });

/**
 * Polígonos derivados da tabela acima — `duval-5.spec.ts` os regenera e falha
 * se alguém editar um vértice na mão.
 *
 * A zona O aparece em DOIS anéis (um no topo, junto do vértice de CH₄, outro
 * no canto de C₂H₆), exatamente como a figura publicada, que também rotula
 * "O" duas vezes. Não é duplicata: são duas regiões disjuntas da mesma zona.
 *
 * A versão anterior era desenhada "pelo layout da tela" e dava diagnóstico
 * diferente do publicado em 67,5% da área.
 */
export const ZONAS_DUVAL_5: readonly ZonaTernaria[] = [
  {
    codigo: 'PD',
    destacada: false,
    vertices: [ponto(98, 0, 2), ponto(97, 1, 2), ponto(85, 1, 14), ponto(86, 0, 14)],
  },
  {
    codigo: 'O',
    destacada: false,
    vertices: [
      ponto(100, 0, 0),
      ponto(90, 10, 0),
      ponto(76, 10, 14),
      ponto(85, 1, 14),
      ponto(97, 1, 2),
      ponto(98, 0, 2),
    ],
  },
  {
    codigo: 'O',
    destacada: false,
    vertices: [ponto(46, 0, 54), ponto(36, 10, 54), ponto(0, 10, 90), ponto(0, 0, 100)],
  },
  {
    codigo: 'S',
    destacada: false,
    vertices: [ponto(86, 0, 14), ponto(76, 10, 14), ponto(36, 10, 54), ponto(46, 0, 54)],
  },
  {
    codigo: 'T2',
    destacada: false,
    vertices: [ponto(90, 10, 0), ponto(65, 35, 0), ponto(53, 35, 12), ponto(78, 10, 12)],
  },
  {
    codigo: 'C',
    destacada: false,
    vertices: [
      ponto(78, 10, 12),
      ponto(38, 50, 12),
      ponto(36, 50, 14),
      ponto(16, 70, 14),
      ponto(0, 70, 30),
      ponto(60, 10, 30),
    ],
  },
  {
    codigo: 'T3',
    destacada: false,
    vertices: [
      ponto(65, 35, 0),
      ponto(0, 100, 0),
      ponto(0, 35, 65),
      ponto(35, 35, 30),
      ponto(0, 70, 30),
      ponto(16, 70, 14),
      ponto(36, 50, 14),
      ponto(38, 50, 12),
      ponto(53, 35, 12),
    ],
  },
  {
    codigo: 'ND',
    destacada: false,
    vertices: [ponto(60, 10, 30), ponto(35, 35, 30), ponto(0, 35, 65), ponto(0, 10, 90)],
  },
];

/** Marca como destacada toda zona em que caiu pelo menos uma leitura. */
export function zonasDuval5Com(codigos: readonly CodigoDeDuval5[]): readonly ZonaTernaria[] {
  const acesas = new Set<string>(codigos);
  return ZONAS_DUVAL_5.map((zona) => ({ ...zona, destacada: acesas.has(zona.codigo) }));
}
