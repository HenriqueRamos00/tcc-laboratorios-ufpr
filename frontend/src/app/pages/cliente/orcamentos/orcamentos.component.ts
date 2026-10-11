import { NgClass } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import {
  abaDaSituacao,
  type ChaveDaAba,
  type SituacaoDaSolicitacao,
} from '@/app/model/analysis-pipeline';

import { Quote } from '@/app/model/quote';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

interface Orcamento {
  id: string;
  code: string;
  proposalName: string;
  description: string;
  createdAt: string;
  company: string;
  externalContact: string;
  estimatedValue?: string;
  status: string;
  category: SituacaoDaSolicitacao | null;
}

interface StatusBadge {
  label: string;
  badgeClass: string;
}

interface FilterTab {
  value: ChaveDaAba;
  label: string;
}

const STATUS_BADGE: Record<SituacaoDaSolicitacao, StatusBadge> = {
  qualificacao: { label: 'Qualificação', badgeClass: 'orcamento-status-pill--qualification' },
  'analise-da-area': { label: 'Em Análise pela Área', badgeClass: 'orcamento-status-pill--analysis' },
  'elaborando-proposta': { label: 'Elaborando Proposta', badgeClass: 'orcamento-status-pill--drafting' },
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

const FILTER_TABS: ReadonlyArray<FilterTab> = [
  { value: 'todos', label: 'Todos' },
  { value: 'pendentes', label: 'Pendentes de aceite' },
  { value: 'andamento', label: 'Em Andamento' },
  { value: 'finalizados', label: 'Finalizados' },
  { value: 'cancelados', label: 'Cancelados' },
];

const FIELD_PLACEHOLDER = '-';

function saudacaoDoDia(agora = new Date()): string {
  const hora = agora.getHours();
  if (hora < 12) return 'Bom dia';
  if (hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

const BRL_FORMATTER = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

const DATE_FORMATTER = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

// O Salesforce manda ISO 8601 com offset: 2026-03-13T13:16:07.000+0000.
function formatCreatedAt(createdDate: string | null): string {
  if (!createdDate) return FIELD_PLACEHOLDER;
  const date = new Date(createdDate);
  return Number.isNaN(date.getTime()) ? FIELD_PLACEHOLDER : DATE_FORMATTER.format(date);
}

const STAGE_BY_NAME: Record<string, SituacaoDaSolicitacao> = {
  'qualificação': 'qualificacao',
  'em análise pela área': 'analise-da-area',
  'em revisão': 'analise-da-area',
  'elaborando proposta': 'elaborando-proposta',
  'negociação': 'negociacao',
  'em negociação': 'negociacao',
  'pendente de aceite': 'negociacao',
  'aprovado pelo cliente': 'aprovado-pelo-cliente',
  'aguardando entrega da amostra': 'aguardando-amostra',
  'fechado ganho': 'recebido',
  'recebido': 'recebido',
  'protocolado': 'protocolado',
  'em execução': 'em-execucao',
  'incompleto': 'incompleto',
  // Etapas que chegam do AutoLAB depois que a amostra entra no laboratório.
  'completo': 'completo',
  'validado': 'validado',
  'finalizado': 'finalizado',
  'elaborando relatório': 'elaborando-relatorio',
  'relatório publicado': 'relatorio-publicado',
  'fechado perdido': 'cancelado',
  'fechado recusado lactec': 'recusado',
  'cancelado': 'cancelado',
  'recusado': 'recusado',
};

function mapStage(rawStage: string): SituacaoDaSolicitacao | null {
  const stage = rawStage.trim().toLowerCase();
  return STAGE_BY_NAME[stage] ?? null;
}

function resolveExternalContact(quote: Quote): string {
  return quote.externalContactName?.trim() || quote.externalContactEmail?.trim() || FIELD_PLACEHOLDER;
}

// Exibe os campos recebidos pela API sem inventar status ou empresa.
function mapQuoteToOrcamento(quote: Quote): Orcamento {
  const hasValue = typeof quote.totalPrice === 'number' && Number.isFinite(quote.totalPrice);

  return {
    id: quote.id?.trim() || '',
    code: quote.code?.trim() || FIELD_PLACEHOLDER,
    proposalName: quote.name?.trim() || FIELD_PLACEHOLDER,
    description: quote.description?.trim() || FIELD_PLACEHOLDER,
    company: quote.companyName?.trim() || FIELD_PLACEHOLDER,
    externalContact: resolveExternalContact(quote),
    createdAt: formatCreatedAt(quote.createdDate),
    estimatedValue: hasValue ? BRL_FORMATTER.format(quote.totalPrice) : undefined,
    status: quote.status?.trim() || quote.stage?.trim() || '',
    category: mapStage(quote.stage?.trim() || quote.status?.trim() || ''),
  };
}

@Component({
  selector: 'app-client-orcamentos',
  standalone: true,
  imports: [NgClass, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './orcamentos.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientOrcamentosComponent {
  private readonly loginService = inject(LoginService);
  private readonly quotesService = inject(QuotesService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly filterTabs = FILTER_TABS;
  readonly activeFilter = signal<ChaveDaAba>('todos');
  readonly searchTerm = signal('');
  readonly loadError = signal(false);
  readonly loading = signal(false);

  readonly firstName = computed(() => {
    const name = this.loginService.currentUser();
    if (!name) return 'Cliente';
    return name.split(/\s+/)[0];
  });

  // Um computed aqui ficaria cacheado: a hora não é signal.
  readonly greeting = saudacaoDoDia();

  readonly mobileFilterLabel = computed(() => {
    const current = this.activeFilter();
    if (current === 'todos') return 'Todos';
    return FILTER_TABS.find((tab) => tab.value === current)?.label ?? 'Todos';
  });

  readonly orcamentos = signal<ReadonlyArray<Orcamento>>([]);

  readonly visibleOrcamentos = computed(() => {
    const filter = this.activeFilter();
    const term = this.searchTerm().trim().toLowerCase();
    return this.orcamentos().filter((orcamento) => {
      const aba = orcamento.category
        ? abaDaSituacao(orcamento.category)
        : orcamento.status.toLowerCase() === 'em andamento' ? 'andamento' : null;
      if (filter !== 'todos' && aba !== filter) return false;
      if (!term) return true;
      return (
        orcamento.code.toLowerCase().includes(term) ||
        orcamento.description.toLowerCase().includes(term) ||
        orcamento.proposalName.toLowerCase().includes(term) ||
        orcamento.company.toLowerCase().includes(term) ||
        orcamento.externalContact.toLowerCase().includes(term)
      );
    });
  });

  constructor() {
    this.loadQuotes();
  }

  loadQuotes(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.loadError.set(false);
    this.quotesService
      .getQuotes()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (quotes) => this.orcamentos.set(quotes.map(mapQuoteToOrcamento)),
        error: () => {
          this.orcamentos.set([]);
          this.loadError.set(true);
        },
      });
  }

  badgeFor(orcamento: Orcamento): StatusBadge {
    if (orcamento.category) {
      return { ...STATUS_BADGE[orcamento.category], label: orcamento.status };
    }
    return {
      label: orcamento.status || 'Status indisponível',
      badgeClass: 'orcamento-status-pill--unknown',
    };
  }

  setFilter(value: ChaveDaAba): void {
    this.activeFilter.set(value);
  }

  showTrackingUnavailable(): void {
    this.snackBar.open(
      'O acompanhamento detalhado ainda não está disponível.',
      'Fechar',
      { duration: 6000 },
    );
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
  }
}
