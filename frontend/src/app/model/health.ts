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
 * O texto com que o laudo escreve "o ensaio foi feito e nada foi detectado". É
 * o único texto de `MeasuredValue` que carrega significado numérico, e por isso
 * é o único que o cálculo reconhece.
 */
export const NOT_DETECTED_TEXT = 'ND';

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
 * - `semProporcao`: o laboratório reportou os três gases e os três deram não
 *   detectado. Não falta dado nenhum, e também não existe proporção: o
 *   triângulo é a razão entre os três, e a razão de zero por zero não é ponto.
 *   É o caso real do protocolo com metano, etileno e acetileno em ND.
 * - `naoAplicavel`: os gases existem, mas a norma proíbe ler esta figura para
 *   esta falha (os triângulos 4 e 5 são refinamentos condicionais do 1 -
 *   IEEE Std C57.104-2019, p. 66). Aqui sobra dado e falta licença.
 *
 * Os quatro continuam na lista, em vez de sumir: sumir com a análise faz a
 * tela mentir sobre quantas leituras existem, e confundir os casos faz a tela
 * culpar o laboratório por falta de ensaio que houve, ou por uma decisão que é
 * da norma.
 */
export type TriangleReading =
  | { readonly kind: 'plotada'; readonly date: string; readonly point: TernaryPoint }
  | { readonly kind: 'incomputavel'; readonly date: string; readonly missingGas: string }
  | { readonly kind: 'semProporcao'; readonly date: string }
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

/**
 * O percentual de um gás dentro do conjunto que um método lê. `null` quando
 * algum gás do conjunto não tem ensaio: percentual é fração de um total, e
 * total com lacuna não é total.
 */
export interface GasPercentage {
  readonly gas: string;
  readonly percentage: number | null;
}

/**
 * O pentágono de Duval visto por uma coleta.
 *
 * Dois campos nulos por dois motivos diferentes, e é de propósito:
 *
 * - `point` nulo é falta de ensaio. O centroide é geometria pura sobre os cinco
 *   percentuais, então ele existe sempre que os cinco gases existem.
 * - `zone` nulo é falta de insumo normativo. A tabela de fronteiras das zonas do
 *   pentágono não está transcrita no projeto, e atribuir zona por posição num
 *   desenho ilustrativo seria inventar o veredito que falta.
 */
export interface PentagonReading {
  readonly date: string;
  /** Centroide em fração do raio do pentágono, no eixo da tela (y cresce para baixo). */
  readonly point: { readonly x: number; readonly y: number } | null;
  readonly notPlotted: string | null;
  readonly zone: PentagonZone | null;
  readonly zoneNotAssessed: string | null;
  readonly percentages: readonly GasPercentage[];
}

/**
 * Uma razão característica entre dois gases. `value` nulo tem dois motivos, e
 * `notComputed` diz qual: gás sem ensaio, ou gás do denominador não detectado,
 * caso em que a razão não existe em vez de ser grande.
 */
/**
 * Uma razão entre dois gases, com os três desfechos que ela tem de verdade.
 *
 * O motivo de não haver razão viaja como caso, e não como frase: frase não dá
 * para percorrer num `switch`, o compilador não cobra o caso novo em quem
 * consome, e o teste fica preso ao texto que o cliente lê, de modo que mudar a
 * redação quebra o teste sem nada ter mudado de comportamento. É a mesma forma
 * de `TriangleReading` e de `TotalizerCheck.result`, pelo mesmo motivo.
 *
 * `denominatorNotDetected` não é hipótese: com `'ND'` entrando como zero
 * medido, o laudo 01439/2026-004 tem duas das três razões da IEC com zero no
 * denominador. Dividir ali devolveria infinito, e infinito num comparador de
 * faixa vira o diagnóstico mais grave da tabela sem ninguém ter medido nada.
 */
export type GasRatioReading =
  | { readonly kind: 'computed'; readonly key: string; readonly label: string; readonly value: number }
  | {
      readonly kind: 'withoutTest';
      readonly key: string;
      readonly label: string;
      readonly missingGas: string;
    }
  | {
      readonly kind: 'denominatorNotDetected';
      readonly key: string;
      readonly label: string;
      readonly denominator: string;
    };

/**
 * Método que lê razões entre gases: a IEC 60599, adotada pela NBR 7274, e
 * Rogers. As razões saem medidas; o código de falha depende da tabela de faixas
 * da norma, que não está no projeto, e por isso vem nulo com o motivo escrito.
 */
export interface RatioMethodReading {
  readonly date: string;
  readonly ratios: readonly GasRatioReading[];
  readonly faultCode: string | null;
  readonly faultCodeNotAssessed: string | null;
}

/** O total de gases combustíveis de uma coleta, em ppm. */
export interface CombustibleGasReading {
  readonly date: string;
  readonly total: number | null;
  readonly notComputed: string | null;
}

export interface IeeeBlock {
  readonly readings: readonly CombustibleGasReading[];
  /** `null` enquanto os limiares da norma não estiverem no projeto. */
  readonly alarm: number | null;
  readonly attention: number | null;
  readonly thresholdsNotAssessed: string | null;
}

export interface GasReadingNBR7274 {
  readonly gas: string;
  readonly value: number | null;
  /** `null` enquanto o valor-limite por gás não estiver no projeto. */
  readonly limit: number | null;
}

export interface Nbr7274Block {
  readonly date: string;
  readonly readings: readonly GasReadingNBR7274[];
  readonly limitsNotAssessed: string | null;
}

/**
 * O que um método concluiu sobre a coleta mais recente que ele pôde ler.
 * `code` vazio e `description` com o motivo é o método que não concluiu: sem
 * isso, o método sem veredito desaparece da lista e a tela deixa de dizer que
 * ele existe.
 */
export interface DiagnosisConclusion {
  readonly method: string;
  readonly code: string;
  readonly description: string;
}

export interface DiagnosisBlock {
  readonly triangles: readonly DuvalTriangle[];
  readonly pentagon: PentagonReading;
  readonly ieee: IeeeBlock;
  /** Os códigos de falha que a norma define, para a tela legendar os vereditos. */
  readonly iecCodes: readonly FaultCode[];
  readonly iec60599: RatioMethodReading;
  readonly nbr7274: Nbr7274Block;
  readonly rogers: RatioMethodReading;
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
   *
   * Hoje o corpo chega sempre nulo e quem calcula é o `DissolvedGasDiagnosisService`, a
   * partir dos gases que já viajam neste mesmo corpo. O campo continua existindo porque
   * continua no contrato: no dia em que a API servir o bloco, o valor servido vence e a
   * tela não muda.
   */
  readonly diagnosis: DiagnosisBlock | null;
  readonly unreadableResults: readonly UnreadableResult[];
  readonly totalizerChecks: readonly TotalizerCheck[];
}
