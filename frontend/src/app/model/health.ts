import type { ZonaDoPentagono } from '@shared/components/duval-pentagon/duval-pentagon.component';

// Indicadores de saúde do transformador (HU-07): ensaios físico-químicos,
// cromatografia de gases dissolvidos e os métodos de diagnóstico derivados.

export type Classificacao = 'normal' | 'alerta' | 'nao-conforme';

export const ROTULO_DA_CLASSIFICACAO: Record<Classificacao, string> = {
  normal: 'Normal',
  alerta: 'Alerta',
  'nao-conforme': 'Não conforme',
};

/** Faixa da classificação, na ordem em que aparece no medidor. */
export const FAIXAS_DE_CONFORMIDADE: readonly {
  chave: Classificacao;
  rotulo: string;
  criterio: string;
}[] = [
  { chave: 'normal', rotulo: 'NORMAL', criterio: 'dentro do limite' },
  { chave: 'alerta', rotulo: 'ALERTA', criterio: 'próximo ao limite' },
  { chave: 'nao-conforme', rotulo: 'NAO CONFORME', criterio: 'excede o limite' },
];

/** Uma coleta: a data e o valor medido de cada indicador naquela data. */
export interface Coleta {
  readonly data: string;
  readonly valores: Readonly<Record<string, number | null>>;
}

/**
 * Uma coluna de resultado na tabela do laudo. A coleta mais recente é a coluna
 * RESULTADO, destacada; as anteriores formam o HISTÓRICO. É a mesma regra nas
 * duas tabelas do laudo, a físico-química e a cromatográfica.
 */
export interface ColunaDeColeta {
  readonly data: string;
  readonly ehResultadoAtual: boolean;
}

/**
 * Um valor medido numa coleta. Os três casos são distintos e o laudo impresso
 * distingue os três:
 *
 * - um número: o ensaio foi feito e deu aquilo.
 * - `'ND'`: o ensaio foi feito e o gás ou a grandeza não foi detectada.
 * - `null`: aquela coleta não foi realizada para este ensaio. Não há medição.
 *
 * Colapsar `'ND'` em `null` faz a tela afirmar ausência de gás onde houve
 * apenas ausência de visita.
 */
export type ValorMedido = number | 'ND' | null;

/**
 * O laudo é texto corrido em parágrafos, não um blocão único: o parágrafo é a
 * unidade que o laboratório escreve e revisa. Guardar a lista preserva essa
 * divisão sem pedir marcação ao template.
 */
export interface LaudoDeEnsaio {
  readonly titulo: string;
  readonly paragrafos: readonly string[];
}

/**
 * Uma linha da tabela físico-química.
 *
 * `valoresPorColeta` é posicional e casa índice a índice com as colunas do
 * bloco: `valoresPorColeta.length === colunas.length`. É lista e não
 * `Record<data, valor>` porque a ordem cronológica é informação, e objeto
 * serializado por backend não promete ordem de chave.
 */
export interface EnsaioFisicoQuimico {
  readonly chave: string;
  readonly nome: string;
  readonly metodo: string;
  /** Valor-limite da ABNT NBR 10576/17 para óleo de transformador em uso. */
  readonly limite: string;
  readonly valoresPorColeta: readonly ValorMedido[];
  readonly classificacao: Classificacao;
}

export interface GasDissolvido {
  readonly chave: string;
  readonly nome: string;
  readonly formula: string;
  readonly resultados: readonly ValorMedido[];
}

export interface BlocoDeEnsaios {
  readonly conformidade: Classificacao;
  readonly veredito: string;
  readonly coletas: readonly Coleta[];
  readonly laudo: LaudoDeEnsaio;
}

export interface BlocoFisicoQuimico extends BlocoDeEnsaios {
  readonly tensaoNominal: string;
  readonly colunas: readonly ColunaDeColeta[];
  readonly ensaios: readonly EnsaioFisicoQuimico[];
}

export interface BlocoDeGases extends BlocoDeEnsaios {
  readonly datasDeColeta: readonly string[];
  readonly gases: readonly GasDissolvido[];
}

// --- Métodos de diagnóstico -------------------------------------------------

/** Ponto plotado num triângulo de Duval, em coordenadas ternárias (somam 100). */
export interface PontoTernario {
  readonly a: number;
  readonly b: number;
  readonly c: number;
}

/**
 * Uma coleta vista pelo triângulo. Nem toda coleta vira ponto, e os dois
 * motivos para isso são diferentes:
 *
 * - `incomputavel`: o laboratório não reportou um dos três gases. Falta dado.
 * - `naoAplicavel`: os gases existem, mas a norma proíbe ler esta figura para
 *   esta falha (os triângulos 4 e 5 são refinamentos condicionais do 1 -
 *   IEEE Std C57.104-2019, p. 66). Aqui sobra dado e falta licença.
 *
 * Os dois continuam na lista, em vez de sumir: sumir com a coleta faz a tela
 * mentir sobre quantas leituras existem, e confundir os dois casos faz a tela
 * culpar o laboratório por uma decisão que é da norma.
 */
export type LeituraDoTriangulo =
  | { readonly tipo: 'plotada'; readonly data: string; readonly ponto: PontoTernario }
  | { readonly tipo: 'incomputavel'; readonly data: string; readonly gasAusente: string }
  | { readonly tipo: 'naoAplicavel'; readonly data: string; readonly motivo: string };

export interface TrianguloDeDuval {
  readonly numero: 1 | 4 | 5;
  readonly eixoEsquerdo: string;
  readonly eixoDireito: string;
  readonly eixoBase: string;
  readonly zonas: readonly ZonaTernaria[];
  readonly leituras: readonly LeituraDoTriangulo[];
  /**
   * Por que nenhuma coleta licenciou esta figura, quando nenhuma licenciou.
   * `null` é o caso normal (figura aplicável, ou o próprio triângulo 1).
   * Serve para a tela explicar um triângulo vazio em vez de deixá-lo mudo.
   */
  readonly naoAplicavel: string | null;
}

/** Zona do triângulo, desenhada por vértices em coordenadas ternárias. */
export interface ZonaTernaria {
  readonly codigo: string;
  readonly vertices: readonly PontoTernario[];
  readonly destacada: boolean;
}

export interface CodigoDeFalha {
  readonly codigo: string;
  readonly descricao: string;
}

export interface LeituraDeGasNBR7274 {
  readonly gas: string;
  readonly valor: number;
  readonly limite: number;
}

export type ResultadoDeRogers = 'termico' | 'descarga' | 'indeterminado';

export interface CelulaDeRogers {
  readonly linha: string;
  readonly coluna: string;
  readonly resultado: ResultadoDeRogers;
}

export interface ConclusaoDeDiagnostico {
  readonly metodo: string;
  readonly codigo: string;
  readonly descricao: string;
}

export interface BlocoDeDiagnostico {
  readonly triangulos: readonly TrianguloDeDuval[];
  readonly pentagono: { readonly zona: ZonaDoPentagono; readonly x: number; readonly y: number };
  readonly ieee: { readonly serie: readonly number[]; readonly alarme: number; readonly atencao: number };
  readonly codigosIec: readonly CodigoDeFalha[];
  readonly nbr7274: readonly LeituraDeGasNBR7274[];
  readonly rogers: readonly CelulaDeRogers[];
  readonly conclusoes: readonly ConclusaoDeDiagnostico[];
}

/**
 * Qual análise do histórico está na tela. Vem junto dos indicadores para a
 * migalha de pão poder nomeá-la sem uma segunda requisição ao histórico.
 */
export interface AnaliseSelecionada {
  readonly id: string;
  readonly data: string;
  readonly rotulo: string;
}

export interface IndicadoresDeSaude {
  readonly equipamentoId: string;
  readonly tag: string;
  /** `null` na visão geral, que não é feita de uma análise só. */
  readonly analiseSelecionada: AnaliseSelecionada | null;
  readonly fisicoQuimico: BlocoFisicoQuimico;
  readonly gasesDissolvidos: BlocoDeGases;
  readonly diagnostico: BlocoDeDiagnostico;
}
