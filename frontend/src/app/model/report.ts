export interface Report {
  readonly id: string;
  readonly identification: string | null;
  readonly requestNumber: string | null;
  readonly quoteCode: string | null;
  readonly sampleId: string | null;
  readonly sampleName: string | null;
  readonly client: string | null;
  readonly status: string | null;
  readonly scheduledDate: string | null;
  readonly issueDate: string | null;
  readonly publicationDate: string | null;
  readonly hasSignedDocument: boolean;
  readonly pdfAvailable?: boolean;
  readonly laboratory?: string | null;
}

/** Compatibility contract for published-report clients introduced by the Spring integration. */
export interface PublishedReport {
  readonly id: string;
  readonly code: string;
  readonly protocol: string;
  readonly sample: string;
  readonly laboratory: string;
  readonly publishedOn: string;
}

export interface AnalysisConclusion {
  readonly quoteId: string;
  readonly quoteCode: string;
  readonly protocol: string;
  readonly testType: string;
  readonly summary: string;
  readonly reports: readonly PublishedReport[];
}
