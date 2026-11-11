import {
  AnalysisClassification,
  AnalysisType,
  InstrumentReference,
  Measurement,
  ProcessStatus,
  TechnicianReference,
} from './ensaio';

export type ReportValidationStatus = 'pending-validation' | 'validated';

export interface TestReportSummary {
  id: string;
  validationStatus: ReportValidationStatus;
  published: boolean;
  publishedAt: string | null;
  version: number;
}

export interface TestReportsResponse {
  items: TestReportSummary[];
  publishedDocumentAvailable: boolean;
}

export interface ReportPreparationResponse {
  outcome: 'created' | 'existing' | 'selection-required';
  reportId: string | null;
  reports: TestReportSummary[];
}

export interface ReportCompanyReference {
  id: string;
  name: string;
}

export interface ReportQuoteReference {
  id: string;
  code: string;
}

export interface ReportSampleReference {
  id: string;
  code: string;
  description: string;
}

export interface ReportSummary {
  id: string;
  company: ReportCompanyReference;
  quote: ReportQuoteReference;
  sample: ReportSampleReference;
  type: string;
  analysisType: AnalysisType;
  validationStatus: ReportValidationStatus;
  published: boolean;
  pdfAvailable: boolean;
  createdAt: string;
  validatedAt: string | null;
  publishedAt: string | null;
  version: number;
}

export interface ReportTestDetail {
  id: string;
  code: string;
  type: string;
  analysisType: AnalysisType;
  method: string;
  standard: string;
  scheduledDate: string;
  technicalStatus: ProcessStatus;
  responsible: TechnicianReference | null;
  analyzedEquipment: {
    id: string;
    tag: string;
    serialNumber: string;
  } | null;
  instruments: InstrumentReference[];
  analysisClassification: AnalysisClassification;
  reportText: string | null;
  measurements: Measurement[];
}

export interface ReportDetail extends ReportSummary {
  contentFingerprint: string;
  validatedBy: TechnicianReference | null;
  publishedBy: TechnicianReference | null;
  test: ReportTestDetail;
}

export interface ReportValidationResponse {
  id: string;
  validationStatus: 'validated';
  published: boolean;
  validatedAt: string;
  validatedBy: TechnicianReference;
  publishedAt: string | null;
  version: number;
  pdfAvailable: boolean;
}

export interface ReportPublicationResponse {
  id: string;
  validationStatus: 'validated';
  published: true;
  publishedAt: string;
  publishedBy: TechnicianReference;
  version: number;
  pdfAvailable: true;
}
