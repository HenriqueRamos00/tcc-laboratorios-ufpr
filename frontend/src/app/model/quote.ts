import type { RequestStatus } from './analysis-pipeline';

// Espelha o payload de Portal.Domain.Quotes.Quote serializado pela API (camelCase).
// GET /api/quotes e GET /api/quotes/{id}
export type Quote = {
  id: string;
  code: string;
  name: string;
  status: string;
  stage: string;
  laboratoryStage?: string | null;
  clientDecision?: ClientDecision | null;
  description: string;
  companyName: string;
  protocol?: string;
  externalContactName: string;
  externalContactEmail: string;
  totalPrice: number;
  createdDate: string | null;
};

export type ClientDecisionKind = 'acceptance' | 'purchase-order' | 'sample-shipment' | 'rejection';

export interface ClientDecision {
  readonly id: string;
  readonly kind: ClientDecisionKind;
  readonly recordedAt: string;
}

const STATUS_BY_DECISION: Record<ClientDecisionKind, RequestStatus> = {
  acceptance: 'aprovado-pelo-cliente',
  'purchase-order': 'aprovado-pelo-cliente',
  'sample-shipment': 'aguardando-amostra',
  rejection: 'recusado',
};

export function statusOfClientDecision(kind: ClientDecisionKind): RequestStatus {
  return STATUS_BY_DECISION[kind];
}
