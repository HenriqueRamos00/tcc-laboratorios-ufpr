import { MeasurementResult, ProcessStatus } from './ensaio';
import { ReportValidationStatus } from './relatorio';

const VALIDATION_LABELS: Record<ReportValidationStatus, string> = {
  'pending-validation': 'Aguardando validação',
  validated: 'Validado',
};

export function validationStatusLabel(status: ReportValidationStatus): string {
  return VALIDATION_LABELS[status] ?? status;
}

export function validationStatusClass(status: ReportValidationStatus): string {
  return status === 'validated'
    ? 'report-status report-status--validated'
    : 'report-status report-status--pending';
}

export function publicationStatusLabel(published: boolean): string {
  return published ? 'Publicado' : 'Não publicado';
}

export function publicationStatusClass(published: boolean): string {
  return published
    ? 'report-publication report-publication--published'
    : 'report-publication report-publication--unpublished';
}

const TECHNICAL_STATUS_LABELS: Record<ProcessStatus, string> = {
  incomplete: 'Em execução',
  complete: 'Completo',
  validated: 'Validado',
  finalized: 'Finalizado',
  'drafting-report': 'Elaborando relatório',
  'report-published': 'Relatório publicado',
  cancelled: 'Cancelado',
};

export function technicalStatusLabel(status: ProcessStatus): string {
  return TECHNICAL_STATUS_LABELS[status] ?? status;
}

export function measurementResultLabel(result: MeasurementResult): string {
  switch (result.kind) {
    case 'quantity':
      return result.value;
    case 'text':
      return result.text;
    case 'not-detected':
      return 'Não detectado';
    case 'not-performed':
      return 'Não realizado';
  }
}

export function formatReportDate(value: string | null | undefined): string {
  if (!value) return 'Não informado';

  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('pt-BR');
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const dateLabel = date.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
  const timeLabel = date.toLocaleTimeString('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });

  return `${dateLabel} ${timeLabel}`;
}

export function reportEventDateLabel(value: string | null | undefined): string {
  return value ? formatReportDate(value) : 'Ainda não ocorreu';
}
