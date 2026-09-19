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

export interface EnsaioFisicoQuimico {
  readonly chave: string;
  readonly nome: string;
  readonly metodo: string;
  /** Valor-limite da ABNT NBR 10576/17 para óleo de transformador em uso. */
  readonly limite: string;
  readonly resultado: number | null;
  readonly classificacao: Classificacao;
}

export interface GasDissolvido {
  readonly chave: string;
  readonly nome: string;
  readonly formula: string;
  readonly resultados: readonly (number | null)[];
}

export interface BlocoDeEnsaios {
  readonly conformidade: Classificacao;
  readonly veredito: string;
  readonly coletas: readonly Coleta[];
  readonly laudo: string;
}

export interface BlocoFisicoQuimico extends BlocoDeEnsaios {
  readonly tensaoNominal: string;
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

export interface TrianguloDeDuval {
  readonly numero: 1 | 4 | 5;
  readonly eixoEsquerdo: string;
  readonly eixoDireito: string;
  readonly eixoBase: string;
  readonly zonas: readonly ZonaTernaria[];
  readonly pontos: readonly PontoTernario[];
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

export interface IndicadoresDeSaude {
  readonly equipamentoId: string;
  readonly tag: string;
  readonly fisicoQuimico: BlocoFisicoQuimico;
  readonly gasesDissolvidos: BlocoDeGases;
  readonly diagnostico: BlocoDeDiagnostico;
}
