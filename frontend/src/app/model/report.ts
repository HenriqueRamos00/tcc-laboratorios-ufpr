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
}
