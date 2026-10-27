export type AnalysisType = 'physical-chemical' | 'chromatography' | null;
export type AnalysisClassification =
  'normal' | 'alerta' | 'nao-conforme' | null;
export type ProcessStatus =
  | 'incomplete'
  | 'complete'
  | 'validated'
  | 'finalized'
  | 'drafting-report'
  | 'report-published'
  | 'cancelled';

export type MeasurementResult =
  | { kind: 'quantity'; value: string }
  | { kind: 'not-detected' }
  | { kind: 'not-performed' }
  | { kind: 'text'; text: string };

export interface MeasurementTemplate {
  code: string;
  name: string;
  unit: string | null;
  method: string | null;
  referenceLimit: string | null;
}

export interface Measurement extends MeasurementTemplate {
  result: MeasurementResult;
}

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
}

export interface QuoteReference {
  id: string;
  code: string;
  companyId: string;
  companyName: string;
}

export interface AnalyzedEquipmentReference {
  id: string;
  tag: string;
  serialNumber: string;
}

export interface SampleReference {
  id: string;
  code: string;
  description: string;
  quote: QuoteReference;
  analyzedEquipment: AnalyzedEquipmentReference | null;
}

export interface TechnicianReference {
  id: string;
  name: string;
  unit: string | null;
  active?: boolean;
}

export interface InstrumentReference {
  id: string;
  inventoryNumber: string;
  name: string;
  calibrationCertificate: string | null;
  calibrationValidUntil: string | null;
}

export interface TestSummary {
  id: string;
  code: string;
  sample: Pick<SampleReference, 'id' | 'code' | 'description'>;
  quote: QuoteReference;
  type: string;
  analysisType: AnalysisType;
  scheduledDate: string;
  responsible: TechnicianReference;
  status: ProcessStatus;
  version: number;
}

export interface TestDetail extends TestSummary {
  method: string;
  standard: string;
  analyzedEquipment: SampleReference['analyzedEquipment'];
  equipmentIds: string[];
  equipment: InstrumentReference[];
  analysisClassification: AnalysisClassification;
  reportText: string | null;
  measurements: Measurement[];
}

export interface TestRequest {
  sampleId: string;
  type: string;
  analysisType: AnalysisType;
  method: string;
  standard: string;
  scheduledDate: string;
  responsibleId: string;
  equipmentIds: string[];
  analysisClassification: AnalysisClassification;
  reportText: string | null;
  measurements: Measurement[];
}

export interface TestUpdateRequest extends TestRequest {
  version: number;
}
