// Equipamento do cliente (HU-05 e HU-06). Origem: AutoLAB.

export interface Equipamento {
  readonly id: string;
  readonly numeroDeSerie: string;
  readonly tag: string;
  readonly tipo: string;
  readonly localizacao: string;
}

export interface DetalheDoEquipamento extends Equipamento {
  readonly tipoDoOleo: string;
  readonly empresa: string;
  readonly tensao: string;
  readonly potencia: string;
}

/**
 * Qual ensaio o laudo contém. A chave é estável e a tradução fica em
 * `ROTULO_DO_TIPO_DE_ANALISE`: antes o tipo era texto livre acentuado, e
 * decidir para onde a linha do histórico navega exigia casar
 * "Análise Físico-Química" com a chave da aba.
 */
export type TipoDeAnalise = 'fisico-quimico' | 'cromatografia' | 'gases-dissolvidos';

export const ROTULO_DO_TIPO_DE_ANALISE: Record<TipoDeAnalise, string> = {
  'fisico-quimico': 'Análise Físico-Química',
  cromatografia: 'Cromatografia',
  'gases-dissolvidos': 'Análise de Gases Dissolvidos',
};

/** Linha do histórico de relatórios exibido no detalhe do equipamento. */
export interface RelatorioDoEquipamento {
  readonly id: string;
  readonly data: string;
  readonly tipoDeAnalise: TipoDeAnalise;
  readonly situacao: 'Concluído' | 'Em andamento' | 'Cancelado';
  /**
   * Endereço do arquivo publicado, quando já existe um. `null` quando o laudo
   * ainda não foi assinado, e aí a tela não oferece download em vez de
   * oferecer um botão que só sabe falhar.
   */
  readonly urlDoRelatorio: string | null;
}
