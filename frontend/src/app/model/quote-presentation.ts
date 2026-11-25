// O orçamento chega pela API com a etapa escrita por extenso e o valor em
// número cru. Aqui ele vira o que a tela mostra, e vira num lugar só: a
// listagem de orçamentos e a tela de relatórios leem a mesma situação, então
// etapa reconhecida de um jeito numa e de outro jeito na outra seria defeito.

import {
  isClosed,
  isPipelineStage,
  stageIndex,
  type PipelineStage,
  type RequestStatus,
} from './analysis-pipeline';
import { statusOfClientDecision, type Quote } from './quote';
import { DataPipe } from '@shared/pipes/data.pipe';
import { MoedaPipe } from '@shared/pipes/moeda.pipe';

/** Um orçamento já resolvido para a tela: rótulo pronto, valor e data legíveis. */
export interface DisplayedQuote {
  readonly id: string;
  readonly code: string;
  readonly protocol?: string;
  readonly proposalName: string;
  readonly description: string;
  readonly company: string;
  readonly externalContact: string;
  readonly createdAt: string;
  readonly estimatedValue?: string;
  readonly status: RequestStatus;
  /** Falso enquanto ninguém escreveu a proposta: não há orçamento a abrir nem a baixar. */
  readonly hasProposal: boolean;
}

export interface StatusBadge {
  readonly label: string;
  readonly badgeClass: string;
}

export const STATUS_BADGE: Record<RequestStatus, StatusBadge> = {
  qualificacao: { label: 'Qualificação', badgeClass: 'bg-lactec-etapa-qualificacao-surface text-lactec-etapa-qualificacao-ink' },
  'analise-da-area': {
    label: 'Em Análise pela Área',
    badgeClass: 'bg-lactec-etapa-analise-da-area-surface text-lactec-etapa-analise-da-area-ink',
  },
  'elaborando-proposta': {
    label: 'Elaborando Proposta',
    badgeClass: 'bg-lactec-etapa-elaborando-proposta-surface text-lactec-etapa-elaborando-proposta-ink',
  },
  negociacao: { label: 'Em Negociação', badgeClass: 'bg-lactec-etapa-negociacao-surface text-lactec-etapa-negociacao-ink' },
  'aprovado-pelo-cliente': {
    label: 'Aprovado pelo Cliente',
    badgeClass: 'bg-lactec-etapa-aprovado-pelo-cliente-surface text-lactec-etapa-aprovado-pelo-cliente-ink',
  },
  'aguardando-amostra': {
    label: 'Aguardando Entrega da Amostra',
    badgeClass: 'bg-lactec-etapa-aguardando-amostra-surface text-lactec-etapa-aguardando-amostra-ink',
  },
  recebido: { label: 'Recebido', badgeClass: 'bg-lactec-etapa-recebido-surface text-lactec-etapa-recebido-ink' },
  protocolado: { label: 'Protocolado', badgeClass: 'bg-lactec-etapa-negociacao-surface text-lactec-etapa-negociacao-ink' },
  'em-execucao': { label: 'Em Execução', badgeClass: 'bg-lactec-etapa-analise-da-area-surface text-lactec-etapa-analise-da-area-ink' },
  completo: { label: 'Completo', badgeClass: 'bg-lactec-etapa-completo-surface text-lactec-etapa-completo-ink' },
  validado: { label: 'Validado', badgeClass: 'bg-lactec-etapa-validado-surface text-lactec-etapa-validado-ink' },
  finalizado: { label: 'Finalizado', badgeClass: 'bg-lactec-etapa-finalizado-surface text-lactec-etapa-finalizado-ink' },
  'elaborando-relatorio': {
    label: 'Elaborando Relatório',
    badgeClass: 'bg-lactec-etapa-elaborando-relatorio-surface text-lactec-etapa-elaborando-relatorio-ink',
  },
  'relatorio-publicado': {
    label: 'Relatório Publicado',
    badgeClass: 'bg-lactec-etapa-relatorio-publicado-surface text-lactec-etapa-relatorio-publicado-ink',
  },
  cancelado: { label: 'Cancelado', badgeClass: 'bg-lactec-etapa-cancelado-surface text-lactec-etapa-cancelado-ink' },
  recusado: { label: 'Recusado', badgeClass: 'bg-lactec-etapa-cancelado-surface text-lactec-etapa-cancelado-ink' },
};

const FIELD_PLACEHOLDER = '—';

const dataPipe = new DataPipe();
const moedaPipe = new MoedaPipe();

// O match exato cobre os valores conhecidos do Salesforce; o regex do
// `recognizeStage` existe para variações futuras não derrubarem a tela.
const STAGE_BY_NAME: Record<string, RequestStatus> = {
  'qualificação': 'qualificacao',
  'em análise pela área': 'analise-da-area',
  'em revisão': 'analise-da-area',
  'elaborando proposta': 'elaborando-proposta',
  'negociação': 'negociacao',
  'aprovado pelo cliente': 'aprovado-pelo-cliente',
  'aguardando entrega da amostra': 'aguardando-amostra',
  'fechado ganho': 'recebido',
  // Etapas que chegam do AutoLAB depois que a amostra entra no laboratório.
  'protocolado': 'protocolado',
  'em execução': 'em-execucao',
  'completo': 'completo',
  'validado': 'validado',
  'finalizado': 'finalizado',
  'elaborando relatório': 'elaborando-relatorio',
  'relatório publicado': 'relatorio-publicado',
  'fechado perdido': 'cancelado',
  'fechado recusado lactec': 'recusado',
  'cancelado': 'cancelado',
};

export function recognizeStage(rawStage: string): RequestStatus {
  const stage = rawStage.trim().toLowerCase();
  const exact = STAGE_BY_NAME[stage];
  if (exact) return exact;

  if (/(perdid|recus|cancel|reject|denied|negad)/.test(stage)) return 'cancelado';
  if (/(ganho|won|recebid|conclu|complete)/.test(stage)) return 'recebido';
  if (/amostra/.test(stage)) return 'aguardando-amostra';
  if (/(aprovad|aceit|accept|approv)/.test(stage)) return 'aprovado-pelo-cliente';
  if (/negocia/.test(stage)) return 'negociacao';
  if (/(elabor|draft|propost)/.test(stage)) return 'elaborando-proposta';
  if (/(análise|analise|revis|review|analysis)/.test(stage)) return 'analise-da-area';
  return 'qualificacao';
}

function recognizeLaboratoryStage(wireName: string | null | undefined): PipelineStage | null {
  const key = wireName?.trim() ?? '';
  return isPipelineStage(key) ? key : null;
}

export function stageOfQuote(
  quote: Pick<Quote, 'stage' | 'laboratoryStage' | 'clientDecision'>,
): RequestStatus {
  const commercial = recognizeStage(quote.stage ?? '');
  if (isClosed(commercial)) return commercial;

  const laboratory = recognizeLaboratoryStage(quote.laboratoryStage);
  const reached = laboratory && stageIndex(laboratory) > stageIndex(commercial) ? laboratory : commercial;
  const decided = quote.clientDecision ? statusOfClientDecision(quote.clientDecision.kind) : undefined;
  return statusAfterDecision(reached, decided);
}


function resolveExternalContact(quote: Quote): string {
  return (
    quote.externalContactName?.trim() || quote.externalContactEmail?.trim() || FIELD_PLACEHOLDER
  );
}

// Sem fallbacks inventados: o protocolo fica ausente quando a fonte não o envia.
export function toDisplayedQuote(quote: Quote): DisplayedQuote {
  const hasValue = typeof quote.totalPrice === 'number' && quote.totalPrice > 0;
  const status = stageOfQuote(quote);

  return {
    id: quote.id?.trim() || '',
    code: quote.code?.trim() || FIELD_PLACEHOLDER,
    protocol: quote.protocol?.trim() || undefined,
    proposalName: quote.name?.trim() || FIELD_PLACEHOLDER,
    description: quote.description?.trim() || '',
    company: quote.companyName?.trim() || FIELD_PLACEHOLDER,
    externalContact: resolveExternalContact(quote),
    createdAt: dataPipe.transform(quote.createdDate) || FIELD_PLACEHOLDER,
    estimatedValue: hasValue ? moedaPipe.transform(quote.totalPrice) : undefined,
    status,
    hasProposal: hasProposal(status),
  };
}

// O orçamento só passa a existir quando alguém termina de escrever a proposta.
// Em qualificação, em análise pela área e elaborando proposta o pedido ainda
// está sendo entendido e precificado: não há documento para baixar nem tela de
// orçamento para abrir, só a situação a acompanhar. Solicitação encerrada
// mantém o orçamento, porque ela só fecha depois que houve proposta.
const FIRST_STAGE_WITH_A_PROPOSAL = 'negociacao';

export function hasProposal(status: RequestStatus): boolean {
  if (isClosed(status)) return true;
  return stageIndex(status) >= stageIndex(FIRST_STAGE_WITH_A_PROPOSAL);
}

// A decisão tomada nesta sessão vale mais que a etapa que veio na carga: o
// Salesforce ainda não soube do aceite nem do envio que acabaram de acontecer
// aqui. Sem isso a listagem manda confirmar outra vez o que o cliente já fez.
export function withSessionDecision(
  quote: DisplayedQuote,
  decided: RequestStatus | undefined,
): DisplayedQuote {
  const status = statusAfterDecision(quote.status, decided);
  return status === quote.status ? quote : { ...quote, status, hasProposal: hasProposal(status) };
}

export function statusAfterDecision(
  loaded: RequestStatus,
  decided: RequestStatus | undefined,
): RequestStatus {
  if (!decided || isClosed(decided) || isClosed(loaded)) return decided ?? loaded;
  return stageIndex(loaded) > stageIndex(decided) ? loaded : decided;
}

export function badgeFor(status: RequestStatus): StatusBadge {
  return STATUS_BADGE[status];
}
