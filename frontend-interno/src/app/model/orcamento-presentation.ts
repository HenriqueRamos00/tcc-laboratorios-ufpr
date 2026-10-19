export const STATUS_ORCAMENTO_CANONICO = [
  'PendingAcceptance',
  'InProgress',
  'Finished',
  'Refused',
  'Cancelled',
] as const;

export type StatusOrcamento = (typeof STATUS_ORCAMENTO_CANONICO)[number];

const ROTULOS_STATUS_ORCAMENTO: Readonly<Record<StatusOrcamento, string>> = {
  PendingAcceptance: 'Pendente de Aceite',
  InProgress: 'Em Andamento',
  Finished: 'Finalizado',
  Refused: 'Recusado',
  Cancelled: 'Cancelado',
};

const CLASSES_STATUS_ORCAMENTO: Readonly<Record<StatusOrcamento, string>> = {
  PendingAcceptance: 'bg-lactec-pill-pend-bg text-lactec-pill-pend-ink',
  InProgress: 'bg-lactec-pill-proc-bg text-lactec-pill-proc-ink',
  Finished: 'bg-lactec-pill-done-bg text-lactec-pill-done-ink',
  Refused: 'bg-lactec-danger-bg text-lactec-danger-ink',
  Cancelled: 'bg-lactec-pill-cancel-bg text-lactec-pill-cancel-ink',
};

const CLASSE_STATUS_DESCONHECIDO = 'bg-lactec-line-soft text-lactec-ink-soft';

export function rotuloStatusOrcamento(status: string): string {
  return Object.hasOwn(ROTULOS_STATUS_ORCAMENTO, status)
    ? ROTULOS_STATUS_ORCAMENTO[status as StatusOrcamento]
    : status;
}

export function classeStatusOrcamento(status: string): string {
  return Object.hasOwn(CLASSES_STATUS_ORCAMENTO, status)
    ? CLASSES_STATUS_ORCAMENTO[status as StatusOrcamento]
    : CLASSE_STATUS_DESCONHECIDO;
}
