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
   * A TAG de placa que o cadastro do AutoLAB conhece, e `null` quando ele não conhece.
   * Preenchida em 55% das linhas do cadastro, então nula com frequência mesmo quando o
   * equipamento tem placa. É campo separado de `tag` de propósito: `tag` nunca é nula e
   * continua sendo o que nomeia o equipamento no título e na trilha de navegação, e é a
   * tela que decide o que imprimir na coluna "TAG".
   */
  readonly equipmentTag: string | null;
  /**
   * `type` vem de `TipoEquipamento` do cadastro, como o cadastro escreve. Nulo quando o
   * cadastro não conhece a série, quando ele se contradiz sobre o tipo, e quando o
   * equipamento é endereçado pelo protocolo, que não é número de série.
   *
   * `location` é nula sempre: o cadastro tem dez colunas e nenhuma delas é localização. O
   * campo continua no contrato para a tela anunciar a ausência em vez de imprimir
   * `undefined` na coluna que a HU-05 pede.
   */
  readonly type: string | null;
  readonly location: string | null;
  readonly company: string;
  readonly identification: string;
  readonly reportCount: number;
  readonly mostRecentReportDate: string;
  /**
   * Se o cadastro tem alguma coisa a dizer sobre este equipamento. Falso quando o
   * equipamento não tem número de série, quando o cadastro não conhece a série, e quando as
   * linhas que ele tem para a série não sustentam nenhum campo.
   *
   * Existe para a tela distinguir "o cadastro não conhece este equipamento" de "o cadastro
   * conhece e esta coluna está em branco". Com os campos nulos sozinhos ela diria
   * "Não informado" sete vezes sem explicar por quê.
   */
  readonly nameplateKnown: boolean;
}

export interface EquipmentDetail extends Equipment {
  /**
   * Dado de placa, campo a campo, como o cadastro escreveu. Nenhum deles ganha unidade que
   * o cadastro não informa: `voltage` traz o texto de `Tensao`, que em 643 linhas tem
   * primário e secundário separados por barra, e `oilVolume` traz o texto de `VolumeOleo`,
   * que tem `1L` e `6 KG` no meio dos números. Só `power` vem montado, de duas colunas, e
   * sem unidade nas 100 linhas que não têm uma.
   */
  readonly oilType: string | null;
  readonly voltage: string | null;
  readonly power: string | null;
  readonly manufacturer: string | null;
  readonly manufacturingYear: string | null;
  readonly oilVolume: string | null;
}

/**
 * O que o laudo mediu. A chave é estável e a tradução fica em
 * `ANALYSIS_TYPE_LABEL`: antes o tipo era texto livre acentuado, e decidir para onde a
 * linha do histórico navega exigia casar "Análise Físico-Química" com a chave da aba.
 */
export type ReportAnalysisType = 'physical-chemical' | 'chromatography' | 'other';

export const ANALYSIS_TYPE_LABEL: Record<ReportAnalysisType, string> = {
  'physical-chemical': 'Análise Físico-Química',
  chromatography: 'Cromatografia',
  other: 'Outros ensaios',
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
  readonly hasSignedDocument: boolean;
}
