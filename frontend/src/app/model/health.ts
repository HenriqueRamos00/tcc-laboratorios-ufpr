import type { PentagonZone } from '@shared/components/duval-pentagon/duval-pentagon.component';

// Indicadores de saúde do transformador (HU-07): ensaios físico-químicos,
// cromatografia de gases dissolvidos e os métodos de diagnóstico derivados.
// Espelha o corpo de GET /api/equipments/{id}/health.

export type Classification = 'normal' | 'alerta' | 'nao-conforme';

export const CLASSIFICATION_LABEL: Record<Classification, string> = {
  normal: 'Normal',
  alerta: 'Alerta',
  'nao-conforme': 'Não conforme',
};

// A classificação chega nula enquanto o núcleo normativo não roda no backend: o
// AutoLAB entrega o valor medido, não o parecer. Nulo não é "normal" nem falha de
// requisição, é ensaio ainda sem parecer, e a tela escreve isso em vez de pintar
// uma faixa que ninguém atribuiu.
export const NOT_ASSESSED_LABEL = 'Não avaliado';

/** Faixa da classificação, na ordem em que aparece no medidor. */
export const CONFORMITY_RANGES: readonly {
  key: Classification;
  label: string;
  criterion: string;
}[] = [
  { key: 'normal', label: 'NORMAL', criterion: 'dentro do limite' },
  { key: 'alerta', label: 'ALERTA', criterion: 'próximo ao limite' },
  { key: 'nao-conforme', label: 'NAO CONFORME', criterion: 'excede o limite' },
];

/** Uma análise: a data e o valor medido de cada indicador naquela data. */
export interface AnalysisReading {
  readonly date: string;
  readonly values: Readonly<Record<string, number | null>>;
}

/**
 * Uma coluna de resultado na tabela do laudo. A análise mais recente é a
 * coluna RESULTADO, destacada; as anteriores formam o HISTÓRICO. É a mesma
 * regra nas duas tabelas do laudo, a físico-química e a cromatográfica.
 */
export interface AnalysisColumn {
  readonly date: string;
  readonly isCurrentResult: boolean;
}

/**
 * Um valor medido numa análise. Os quatro casos são distintos e o laudo
 * impresso distingue os quatro:
 *
 * - um número: o ensaio foi feito e deu aquilo.
 * - `'ND'`: o ensaio foi feito e o gás ou a grandeza não foi detectada.
 * - qualquer outro texto: o ensaio foi feito e o laboratório escreveu algo que
 *   não é número, como `'0,01%'`. A célula mostra o texto como veio.
 * - `null`: aquele ensaio não foi realizado naquela análise. Não há medição.
 *
 * Colapsar `'ND'` em `null` faz a tela afirmar ausência de gás onde houve
 * apenas ausência de ensaio, e colapsar o texto cru em `null` afirma que o
 * laboratório nem esteve lá. O tipo do TypeScript não consegue separar `'ND'`
 * do texto cru, porque `'ND'` é um texto; quem separa os dois é esta descrição
 * e o domínio do backend, onde as quatro formas são tipos fechados.
 */
export type MeasuredValue = number | string | null;

/**
 * O laudo é texto corrido em parágrafos, não um blocão único: o parágrafo é a
 * unidade que o laboratório escreve e revisa. Guardar a lista preserva essa
 * divisão sem pedir marcação ao template.
 */
export interface TestReport {
  readonly title: string;
  readonly paragraphs: readonly string[];
}

/**
 * Uma linha da tabela físico-química.
 *
 * `valuesByAnalysis` é posicional e casa índice a índice com as colunas do
 * bloco: `valuesByAnalysis.length === columns.length`. É lista e não
 * `Record<data, valor>` porque a ordem cronológica é informação, e objeto
 * serializado por backend não promete ordem de chave.
 */
export interface PhysicalChemicalTest {
  readonly key: string;
  readonly name: string;
  readonly method: string;
  /** Valor-limite da ABNT NBR 10576/17 para óleo de transformador em uso. */
  readonly limit: string;
  readonly valuesByAnalysis: readonly MeasuredValue[];
  /** `null` enquanto o núcleo normativo não roda no backend. */
  readonly classification: Classification | null;
}

export interface DissolvedGas {
  readonly key: string;
  readonly name: string;
  readonly formula: string;
  readonly results: readonly MeasuredValue[];
}

export interface TestBlock {
  /** `null` enquanto o núcleo normativo não roda no backend. */
  readonly conformity: Classification | null;
  readonly verdict: string | null;
  readonly analyses: readonly AnalysisReading[];
  readonly report: TestReport;
}

export interface PhysicalChemicalBlock extends TestBlock {
  readonly nominalVoltage: string;
  readonly columns: readonly AnalysisColumn[];
  readonly tests: readonly PhysicalChemicalTest[];
}

export interface DissolvedGasBlock extends TestBlock {
  readonly analysisDates: readonly string[];
  readonly gases: readonly DissolvedGas[];
}

// --- Métodos de diagnóstico -------------------------------------------------

/** Ponto plotado num triângulo de Duval, em coordenadas ternárias (somam 100). */
export interface TernaryPoint {
  readonly a: number;
  readonly b: number;
  readonly c: number;
}

/**
 * Uma análise vista pelo triângulo. Nem toda análise vira ponto, e os dois
 * motivos para isso são diferentes:
 *
 * - `incomputavel`: o laboratório não reportou um dos três gases. Falta dado.
 * - `naoAplicavel`: os gases existem, mas a norma proíbe ler esta figura para
 *   esta falha (os triângulos 4 e 5 são refinamentos condicionais do 1 -
 *   IEEE Std C57.104-2019, p. 66). Aqui sobra dado e falta licença.
 *
 * Os dois continuam na lista, em vez de sumir: sumir com a análise faz a tela
 * mentir sobre quantas leituras existem, e confundir os dois casos faz a tela
 * culpar o laboratório por uma decisão que é da norma.
 */
export type TriangleReading =
  | { readonly kind: 'plotada'; readonly date: string; readonly point: TernaryPoint }
  | { readonly kind: 'incomputavel'; readonly date: string; readonly missingGas: string }
  | { readonly kind: 'naoAplicavel'; readonly date: string; readonly reason: string };

export interface DuvalTriangle {
  readonly number: 1 | 4 | 5;
  readonly leftAxis: string;
  readonly rightAxis: string;
  readonly baseAxis: string;
  readonly zones: readonly TernaryZone[];
  readonly readings: readonly TriangleReading[];
  /**
   * Por que nenhuma análise licenciou esta figura, quando nenhuma licenciou.
   * `null` é o caso normal (figura aplicável, ou o próprio triângulo 1).
   * Serve para a tela explicar um triângulo vazio em vez de deixá-lo mudo.
   */
  readonly notApplicable: string | null;
}

/** Zona do triângulo, desenhada por vértices em coordenadas ternárias. */
export interface TernaryZone {
  readonly code: string;
  readonly vertices: readonly TernaryPoint[];
  readonly highlighted: boolean;
}

export interface FaultCode {
  readonly code: string;
  readonly description: string;
}

export interface GasReadingNBR7274 {
  readonly gas: string;
  readonly value: number;
  readonly limit: number;
}

export type RogersResult = 'termico' | 'descarga' | 'indeterminado';

export interface RogersCell {
  readonly row: string;
  readonly column: string;
  readonly result: RogersResult;
}

export interface DiagnosisConclusion {
  readonly method: string;
  readonly code: string;
  readonly description: string;
}

export interface DiagnosisBlock {
  readonly triangles: readonly DuvalTriangle[];
  readonly pentagon: { readonly zone: PentagonZone; readonly x: number; readonly y: number };
  readonly ieee: {
    readonly series: readonly number[];
    readonly alarm: number;
    readonly attention: number;
  };
  readonly iecCodes: readonly FaultCode[];
  readonly nbr7274: readonly GasReadingNBR7274[];
  readonly rogers: readonly RogersCell[];
  readonly conclusions: readonly DiagnosisConclusion[];
}

/**
 * Qual análise do histórico está na tela. Vem junto dos indicadores para a
 * migalha de pão poder nomeá-la sem uma segunda requisição ao histórico.
 */
export interface SelectedAnalysis {
  readonly id: string;
  readonly date: string;
  readonly label: string;
}

/**
 * Um item medido cujo resultado chegou como texto que nenhuma estratégia de parse
 * aceita, como "0,01%". Sai da tabela e é declarado aqui, porque uma célula
 * ruim degrada a própria linha e não o laudo inteiro. A tela ainda não mostra
 * esta lista; ela está no tipo porque está no corpo.
 */
export interface UnreadableResult {
  readonly key: string;
  readonly name: string;
  readonly originalText: string;
  readonly protocoloLactec: string;
}

/**
 * A conferência de um totalizador publicado contra a soma dos gases. Três
 * desfechos, e não dois: com gás sem ensaio a soma não é comparável, e aí não
 * é divergência nenhuma. A tela ainda não mostra; está no tipo porque está no
 * corpo.
 */
export interface TotalizerCheck {
  readonly key: string;
  readonly publishedValue: number;
  readonly measuredSum: number;
  readonly gasesWithoutTest: number;
  readonly result: 'matches' | 'diverges' | 'incomplete-sum';
}

export interface HealthIndicators {
  readonly equipmentId: string;
  readonly tag: string;
  /** `null` na visão geral, que não é feita de uma análise só. */
  readonly selectedAnalysis: SelectedAnalysis | null;
  readonly physicalChemical: PhysicalChemicalBlock;
  readonly dissolvedGases: DissolvedGasBlock;
  /**
   * `null` enquanto o núcleo normativo não roda no backend, e também em laudo de área
   * que não é Óleos, que não tem gás dissolvido nenhum para diagnosticar. O campo era
   * obrigatório no tipo e chegava nulo no corpo: o `?.` do componente curto-circuitava só
   * no envelope, e a leitura seguinte derrubava a tela inteira com `TypeError`.
   */
  readonly diagnosis: DiagnosisBlock | null;
  readonly unreadableResults: readonly UnreadableResult[];
  readonly totalizerChecks: readonly TotalizerCheck[];
}
