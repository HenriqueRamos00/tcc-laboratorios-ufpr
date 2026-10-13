// O orçamento chega do Salesforce com a etapa escrita por extenso e o valor em
// número cru. Aqui ele vira o que a tela mostra, e vira num lugar só: a
// listagem de orçamentos e a tela de relatórios leem a mesma situação, então
// etapa reconhecida de um jeito numa e de outro jeito na outra seria defeito.

import { ehTerminal, indiceDaEtapa, type SituacaoDaSolicitacao } from './analysis-pipeline';
import type { Quote } from './quote';

/** Um orçamento já resolvido para a tela: rótulo pronto, valor e data legíveis. */
export interface DisplayedQuote {
  readonly id: string;
  readonly code: string;
  readonly proposalName: string;
  readonly company: string;
  readonly externalContact: string;
  readonly createdAt: string;
  readonly estimatedValue?: string;
  readonly status: SituacaoDaSolicitacao;
  /** Falso enquanto ninguém escreveu a proposta: não há orçamento a abrir nem a baixar. */
  readonly hasProposal: boolean;
}

export interface StatusBadge {
  readonly label: string;
  readonly badgeClass: string;
}

export const STATUS_BADGE: Record<SituacaoDaSolicitacao, StatusBadge> = {
  qualificacao: { label: 'Qualificação', badgeClass: 'orcamento-status-pill--qualification' },
  'analise-da-area': {
    label: 'Em Análise pela Área',
    badgeClass: 'orcamento-status-pill--analysis',
  },
  'elaborando-proposta': {
    label: 'Elaborando Proposta',
    badgeClass: 'orcamento-status-pill--drafting',
  },
  negociacao: { label: 'Em Negociação', badgeClass: 'orcamento-status-pill--negotiation' },
  'aprovado-pelo-cliente': {
    label: 'Aprovado pelo Cliente',
    badgeClass: 'orcamento-status-pill--accepted',
  },
  'aguardando-amostra': {
    label: 'Aguardando Entrega da Amostra',
    badgeClass: 'orcamento-status-pill--awaiting-sample',
  },
  recebido: { label: 'Recebido', badgeClass: 'orcamento-status-pill--received' },
  protocolado: { label: 'Protocolado', badgeClass: 'orcamento-status-pill--protocoled' },
  'em-execucao': { label: 'Em Execução', badgeClass: 'orcamento-status-pill--running' },
  incompleto: { label: 'Incompleto', badgeClass: 'orcamento-status-pill--incomplete' },
  completo: { label: 'Completo', badgeClass: 'orcamento-status-pill--complete' },
  validado: { label: 'Validado', badgeClass: 'orcamento-status-pill--validated' },
  finalizado: { label: 'Finalizado', badgeClass: 'orcamento-status-pill--finalized' },
  'elaborando-relatorio': {
    label: 'Elaborando Relatório',
    badgeClass: 'orcamento-status-pill--drafting-report',
  },
  'relatorio-publicado': {
    label: 'Relatório Publicado',
    badgeClass: 'orcamento-status-pill--report-published',
  },
  cancelado: { label: 'Cancelado', badgeClass: 'orcamento-status-pill--cancelled' },
  recusado: { label: 'Recusado', badgeClass: 'orcamento-status-pill--cancelled' },
};

const FIELD_PLACEHOLDER = '—';

const BRL_FORMATTER = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

const DATE_FORMATTER = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

// O match exato cobre os valores conhecidos do Salesforce; o regex do
// `recognizeStage` existe para variações futuras não derrubarem a tela.
const STAGE_BY_NAME: Record<string, SituacaoDaSolicitacao> = {
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
  'incompleto': 'incompleto',
  'completo': 'completo',
  'validado': 'validado',
  'finalizado': 'finalizado',
  'elaborando relatório': 'elaborando-relatorio',
  'relatório publicado': 'relatorio-publicado',
  'fechado perdido': 'cancelado',
  'fechado recusado lactec': 'recusado',
  'cancelado': 'cancelado',
};

export function recognizeStage(rawStage: string): SituacaoDaSolicitacao {
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

// O Salesforce manda ISO 8601 com offset: 2026-03-13T13:16:07.000+0000.
function readableCreatedDate(createdDate: string | null): string {
  if (!createdDate) return FIELD_PLACEHOLDER;
  const date = new Date(createdDate);
  return Number.isNaN(date.getTime()) ? FIELD_PLACEHOLDER : DATE_FORMATTER.format(date);
}

function resolveExternalContact(quote: Quote): string {
  return (
    quote.externalContactName?.trim() || quote.externalContactEmail?.trim() || FIELD_PLACEHOLDER
  );
}

// Sem fallbacks inventados: exibimos apenas o que vem do Salesforce.
export function toDisplayedQuote(quote: Quote): DisplayedQuote {
  const hasValue = typeof quote.totalPrice === 'number' && quote.totalPrice > 0;
  const status = recognizeStage(quote.stage ?? '');

  return {
    id: quote.id?.trim() || '',
    code: quote.code?.trim() || FIELD_PLACEHOLDER,
    proposalName: quote.name?.trim() || FIELD_PLACEHOLDER,
    company: quote.companyName?.trim() || FIELD_PLACEHOLDER,
    externalContact: resolveExternalContact(quote),
    createdAt: readableCreatedDate(quote.createdDate),
    estimatedValue: hasValue ? BRL_FORMATTER.format(quote.totalPrice) : undefined,
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

export function hasProposal(status: SituacaoDaSolicitacao): boolean {
  if (ehTerminal(status)) return true;
  return indiceDaEtapa(status) >= indiceDaEtapa(FIRST_STAGE_WITH_A_PROPOSAL);
}

export function badgeFor(status: SituacaoDaSolicitacao): StatusBadge {
  return STATUS_BADGE[status];
}
