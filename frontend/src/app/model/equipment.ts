// Equipamento do cliente (HU-05 e HU-06). Origem: AutoLAB, pela API do portal.
// Espelha o corpo de GET /api/equipments e GET /api/equipments/{id}.

export interface Equipment {
  readonly id: string;
  /**
   * Nulo quando o laudo veio com a coluna em branco, o que acontece no dado real. A tela
   * mostra a identificação do laudo no lugar, e é por isso que `tag` nunca é nula.
   */
  readonly serialNumber: string | null;
  readonly tag: string;
  /**
   * Nulos sempre, por enquanto. O AutoLAB não tem cadastro de equipamento: não existe
   * coluna de tipo nem de localização em lugar nenhum da fonte. O campo continua no
   * contrato para a tela anunciar a ausência em vez de imprimir `undefined` na coluna que
   * a HU-05 pede.
   */
  readonly type: string | null;
  readonly location: string | null;
  readonly company: string;
  readonly identification: string;
  readonly reportCount: number;
  readonly mostRecentReportDate: string;
}

export interface EquipmentDetail extends Equipment {
  /** Dado de placa, que também não existe no AutoLAB. Mesma regra de `type`. */
  readonly oilType: string | null;
  readonly voltage: string | null;
  readonly power: string | null;
}

/**
 * O que o laudo mediu. A chave é estável e a tradução fica em
 * `ANALYSIS_TYPE_LABEL`: antes o tipo era texto livre acentuado, e decidir para onde a
 * linha do histórico navega exigia casar "Análise Físico-Química" com a chave da aba.
 */
export type ReportAnalysisType = 'physical-chemical' | 'chromatography';

export const ANALYSIS_TYPE_LABEL: Record<ReportAnalysisType, string> = {
  'physical-chemical': 'Análise Físico-Química',
  chromatography: 'Cromatografia',
};

/**
 * Em que pé está o laudo. São dois casos porque são os dois que o dado sustenta: a API
 * deriva a situação da data de emissão, e não há como deduzir cancelamento de uma view que
 * lista item medido medido.
 */
export type ReportStatus = 'completed' | 'in-progress';

export const REPORT_STATUS_LABEL: Record<ReportStatus, string> = {
  completed: 'Concluído',
  'in-progress': 'Em andamento',
};

/** Linha do histórico de relatórios exibido no detalhe do equipamento. */
export interface EquipmentReport {
  readonly id: string;
  readonly date: string;
  readonly analysisType: ReportAnalysisType;
  readonly status: ReportStatus;
  /**
   * Endereço do arquivo publicado, quando já existe um. `null` enquanto o AutoLAB desta
   * fatia serve resultado de ensaio e não o PDF assinado, e aí a tela não oferece download
   * em vez de oferecer um botão que só sabe falhar.
   */
  readonly reportUrl: string | null;
}
