// Laudos publicados ao fim da análise (HU-04). O laudo nasce de um orçamento, e
// é por esse vínculo que a tela de relatórios consegue mostrar só os laudos de
// um orçamento quando o cliente chega nela vindo da listagem de orçamentos.

export interface PublishedReport {
  readonly id: string;
  readonly code: string;
  readonly protocol: string;
  readonly sample: string;
  readonly laboratory: string;
  readonly publishedOn: string;
}

/** O desfecho da análise de um orçamento: o protocolo do laboratório e os laudos que saíram dele. */
export interface AnalysisConclusion {
  readonly quoteId: string;
  readonly quoteCode: string;
  readonly protocol: string;
  readonly testType: string;
  readonly summary: string;
  readonly reports: readonly PublishedReport[];
}
