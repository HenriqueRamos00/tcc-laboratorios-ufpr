import {
  AnalysisClassification,
  AnalysisType,
  Measurement,
  MeasurementResult,
  MeasurementResultKind,
  MeasurementTemplate,
  ProcessStatus,
} from './ensaio';

export const ANALYSIS_TYPES: {
  value: AnalysisType | 'other';
  label: string;
}[] = [
  { value: 'physical-chemical', label: 'Físico-químico' },
  { value: 'chromatography', label: 'Cromatografia' },
  { value: 'other', label: 'Outro tipo' },
];
export const ANALYSIS_CLASSIFICATIONS: {
  value: AnalysisClassification;
  label: string;
}[] = [
  { value: null, label: 'Não classificada' },
  { value: 'normal', label: 'Normal' },
  { value: 'alerta', label: 'Alerta' },
  { value: 'nao-conforme', label: 'Não conforme' },
];
const processLabels: Record<ProcessStatus, string> = {
  incomplete: 'Em execução',
  complete: 'Completo',
  validated: 'Validado',
  finalized: 'Finalizado',
  'drafting-report': 'Elaborando relatório',
  'report-published': 'Relatório publicado',
  cancelled: 'Cancelado',
};
export function processStatusLabel(value: ProcessStatus): string {
  return processLabels[value] ?? value;
}
export function classificationLabel(value: AnalysisClassification): string {
  return (
    ANALYSIS_CLASSIFICATIONS.find((item) => item.value === value)?.label ??
    'Não classificada'
  );
}
export function resultToInput(result: MeasurementResult): string {
  if (result.kind === 'quantity') return result.value.replace('.', ',');
  if (result.kind === 'text') return result.text;
  return '';
}
export function resultFromInput(
  raw: string,
  kind: MeasurementResultKind,
): MeasurementResult {
  const value = raw.trim();
  if (kind === 'not-detected') return { kind };
  if (kind === 'not-performed') return { kind };
  if (kind === 'quantity') {
    if (!/^[+-]?\d+(?:[,.]\d+)?$/.test(value))
      return { kind, value: raw };
    const sign = value.startsWith('-') ? '-' : '';
    const unsigned = value.replace(/^[+-]/, '').replace(',', '.');
    const [integerPart, decimalPart] = unsigned.split('.');
    const normalizedInteger = integerPart.replace(/^0+(?=\d)/, '');
    return {
      kind: 'quantity',
      value: `${sign}${normalizedInteger}${decimalPart === undefined ? '' : `.${decimalPart}`}`,
    };
  }
  return { kind: 'text', text: raw };
}
export function blankMeasurement(template: MeasurementTemplate): Measurement {
  return { ...template, result: { kind: 'not-performed' } };
}
export function isMeasurementFilled(measurement: Measurement): boolean {
  return measurement.result.kind !== 'not-performed';
}
export function statusLabel(value: ProcessStatus): string {
  return processStatusLabel(value);
}

export function isValidQuantityInput(raw: string): boolean {
  const value = raw.trim();
  if (!/^[+-]?\d+(?:[,.]\d+)?$/.test(value)) return false;
  const unsigned = value.replace(/^[+-]/, '');
  const [integerPart, decimalPart = ''] = unsigned.split(/[,.]/);
  const significantInteger = integerPart.replace(/^0+(?=\d)/, '');
  return significantInteger.length <= 16 && decimalPart.length <= 8;
}

export function measurementResultKind(result: MeasurementResult): MeasurementResultKind {
  return result.kind;
}

export function formatCivilDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) return value;
  return new Date(year, month - 1, day).toLocaleDateString('pt-BR');
}
