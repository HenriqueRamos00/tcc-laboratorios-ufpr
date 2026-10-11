// Pentágono de Duval 1 - geometria do ponto medido, sem veredito de zona.
//
// PROVENIÊNCIA E GRAU DE CONFIANÇA (leia antes de mexer num número):
//
// Fonte do método: Duval e Lamarre, "The Duval Pentagon - a new complementary
// tool for the interpretation of dissolved gas analysis in transformers"
// (IEEE Electr. Insul. Mag., 2014). O artigo é pago e NÃO foi lido por ninguém
// desta auditoria.
//
// O QUE ESTE MÓDULO CALCULA, e por que só isso:
//
// 1. Os cinco percentuais relativos (H₂, C₂H₆, CH₄, C₂H₄, C₂H₂ sobre a soma dos
//    cinco). É aritmética sobre o valor medido: não depende de norma nenhuma.
// 2. O centroide do pentágono formado por esses cinco pontos, que é o marcador
//    que a figura publica. Também é geometria pura, uma vez fixada a ordem dos
//    eixos.
//
// O que ele NÃO calcula é a ZONA. A tabela de fronteiras das sete zonas do
// pentágono 1 (PD, D1, D2, T1, T2, T3 e S) não está transcrita no projeto, e
// decidir zona por ponto-em-polígono sobre um desenho ilustrativo seria
// fabricar exatamente o veredito que falta. O desenho desta casa tem cinco
// setores rotulados e a norma publica sete zonas: o desenho é ilustrativo e não
// serve de tabela. Por isso a zona sai nula com o motivo escrito, no mesmo
// padrão de `MeasuredValue` e de `TriangleReading`.
//
// ORDEM DOS EIXOS - confiança MÉDIA: %H₂ no topo e os demais a 72° no sentido
// horário (C₂H₆, CH₄, C₂H₄, C₂H₂). Vem de convergência de fontes secundárias,
// não da leitura da figura publicada. A orientação posiciona o marcador e não
// decide veredito nenhum aqui, justamente porque a zona fica não avaliada.

import type { GasPercentage } from './health';

/** Os cinco gases do pentágono, em ppm. */
export interface PentagonGases {
  readonly h2: number | null;
  readonly c2h6: number | null;
  readonly ch4: number | null;
  readonly c2h4: number | null;
  readonly c2h2: number | null;
}

/** Ponto no plano do desenho: y cresce para baixo, como em SVG. */
export interface ScreenPoint {
  readonly x: number;
  readonly y: number;
}

interface PentagonAxis {
  readonly key: keyof PentagonGases;
  readonly formula: string;
  readonly angleInDegrees: number;
}

/**
 * Fonte única da ordem dos eixos: percentuais e centroide saem desta lista, e
 * não de duas listas que podem divergir. Ângulo em coordenada matemática, com
 * y para cima e 90° no topo.
 */
export const PENTAGON_AXES: readonly PentagonAxis[] = [
  { key: 'h2', formula: 'H₂', angleInDegrees: 90 },
  { key: 'c2h6', formula: 'C₂H₆', angleInDegrees: 18 },
  { key: 'ch4', formula: 'CH₄', angleInDegrees: -54 },
  { key: 'c2h4', formula: 'C₂H₄', angleInDegrees: -126 },
  { key: 'c2h2', formula: 'C₂H₂', angleInDegrees: 162 },
];

export const PENTAGON_ZONE_NOT_ASSESSED =
  'A tabela de fronteiras das zonas do pentágono de Duval não está transcrita no projeto, ' +
  'então o ponto é medido e a zona não é atribuída.';

// Área duplicada abaixo deste valor é polígono degenerado, não figura fina: com
// percentuais em escala de 0 a 100, qualquer pentágono com área de verdade
// passa disto com folga.
const SMALLEST_AREA_WITH_CENTROID = 1e-9;

/** Qual gás faltou, para a tela conseguir dizer por que não plotou. */
export function missingPentagonGas(gases: PentagonGases): string | null {
  const missing = PENTAGON_AXES.find((axis) => gases[axis.key] == null);
  return missing ? missing.formula : null;
}

/**
 * Percentual de cada gás sobre a soma dos cinco. Devolve percentual nulo em
 * todos quando falta ensaio de algum deles ou quando a soma é zero: percentual
 * é fração de um total, e não existe fração de total ausente nem de total zero.
 */
export function pentagonPercentages(gases: PentagonGases): readonly GasPercentage[] {
  const total = sumOfPentagonGases(gases);
  if (total === null || total <= 0) {
    return PENTAGON_AXES.map((axis) => ({ gas: axis.formula, percentage: null }));
  }
  return PENTAGON_AXES.map((axis) => ({
    gas: axis.formula,
    percentage: ((gases[axis.key] ?? 0) / total) * 100,
  }));
}

/**
 * Centroide do pentágono formado pelos cinco percentuais, em fração do raio da
 * figura, já no eixo da tela.
 *
 * Devolve `null` em três casos, e os três são reais: falta ensaio de algum dos
 * cinco gases; a soma dos cinco é zero, ou seja nada foi detectado; e o
 * polígono degenera numa reta, o que acontece quando só um gás foi detectado e
 * os outros quatro caem na origem. Centroide de figura sem área não existe.
 */
export function pentagonCentroid(gases: PentagonGases): ScreenPoint | null {
  const percentages = pentagonPercentages(gases);
  if (percentages.some((item) => item.percentage === null)) return null;

  const vertices = percentages.map((item, index) => {
    const angleInRadians = (PENTAGON_AXES[index].angleInDegrees * Math.PI) / 180;
    const radius = item.percentage ?? 0;
    return { x: radius * Math.cos(angleInRadians), y: radius * Math.sin(angleInRadians) };
  });

  let doubledArea = 0;
  let weightedX = 0;
  let weightedY = 0;
  for (let index = 0; index < vertices.length; index += 1) {
    const current = vertices[index];
    const next = vertices[(index + 1) % vertices.length];
    const crossProduct = current.x * next.y - next.x * current.y;
    doubledArea += crossProduct;
    weightedX += (current.x + next.x) * crossProduct;
    weightedY += (current.y + next.y) * crossProduct;
  }
  if (Math.abs(doubledArea) < SMALLEST_AREA_WITH_CENTROID) return null;

  const centroidX = weightedX / (3 * doubledArea);
  const centroidY = weightedY / (3 * doubledArea);
  return { x: centroidX / 100, y: -centroidY / 100 };
}

function sumOfPentagonGases(gases: PentagonGases): number | null {
  let total = 0;
  for (const axis of PENTAGON_AXES) {
    const concentration = gases[axis.key];
    if (concentration == null) return null;
    total += concentration;
  }
  return total;
}
