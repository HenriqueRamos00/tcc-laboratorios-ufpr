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
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';

import { StatusStepperStep } from '@shared/components/status-stepper/status-stepper.component';

import {
  abaDaSituacao,
  type ChaveDaAba,
  ehTerminal,
  type EtapaDaLinha,
  grupoDaEtapa,
  indiceDaEtapa,
  LINHA_DE_ETAPAS,
  type SituacaoDaSolicitacao,
} from '@/app/model/analysis-pipeline';

import { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

import {
  StatusPropostaDialogComponent,
  StatusPropostaDialogData,
} from './status-proposta-dialog/status-proposta-dialog.component';

interface PendingReviewNotice {
  deadline: string;
  message: string;
  reminder?: string;
}

interface Orcamento {
  id: string;
  code: string;
  proposalName: string;
  createdAt: string;
  company: string;
  externalContact: string;
  estimatedValue?: string;
  status: SituacaoDaSolicitacao;
  pendingReview?: PendingReviewNotice;
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

const PROPOSAL_STEPS: ReadonlyArray<StatusStepperStep> = LINHA_DE_ETAPAS.map((etapa) => ({
  key: etapa.chave,
  label: etapa.rotulo,
  icon: etapa.icone,
}));

const AVISO_DA_ETAPA: Partial<
  Record<EtapaDaLinha, { titulo: string; texto: string; acao?: string }>
> = {
  'analise-da-area': {
    titulo: 'Estamos em busca da melhor solução para seu negócio',
    texto:
      'Nossos profissionais estão validando qual é o laboratório capaz de solucionar e trazer ' +
      'resultados para cada uma das análises requisitadas. Em breve seu orçamento ficará ' +
      'disponível por meio desta plataforma!',
  },
  negociacao: {
    titulo: 'Aguardando sua revisão',
    texto:
      'Esta proposta técnica está pronta para execução. Por favor, revise os termos e custos ' +
      'associados para prosseguir com a análise laboratorial.',
    acao: 'Ver o orçamento',
  },
  'aprovado-pelo-cliente': {
    titulo: 'Aguardando sua revisão',
    texto:
      'Esta proposta técnica está pronta para execução. Por favor, revise os termos e custos ' +
      'associados para prosseguir com a análise laboratorial.',
    acao: 'Ver o orçamento',
  },
};

// Placeholder de demonstração: o contato real vem do AutoLAB junto do
// protocolo, e não pode ser endereço de pessoa fixo no bundle.
const CONTATO_DO_LABORATORIO = 'atendimento@exemplo.com.br';


const FILTER_TABS: ReadonlyArray<FilterTab> = [
  { value: 'todos', label: 'Todos' },
  { value: 'pendentes', label: 'Pendentes de aceite' },
  { value: 'andamento', label: 'Em Andamento' },
  { value: 'finalizados', label: 'Finalizados' },
  { value: 'cancelados', label: 'Cancelados' },
];

const FIELD_PLACEHOLDER = '—';

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

// O match exato cobre os valores conhecidos do Salesforce; o regex abaixo
// existe para variações futuras não derrubarem a tela.
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
  'completo': 'completo',
  'validado': 'validado',
  'finalizado': 'finalizado',
  'elaborando relatório': 'elaborando-relatorio',
  'relatório publicado': 'relatorio-publicado',
  'fechado perdido': 'cancelado',
  'fechado recusado lactec': 'recusado',
  'cancelado': 'cancelado',
};

function mapStage(rawStage: string): SituacaoDaSolicitacao {
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

function resolveExternalContact(quote: Quote): string {
  return quote.externalContactName?.trim() || quote.externalContactEmail?.trim() || FIELD_PLACEHOLDER;
}

// Sem fallbacks inventados: exibimos apenas o que vem do Salesforce.
function mapQuoteToOrcamento(quote: Quote): Orcamento {
  const hasValue = typeof quote.totalPrice === 'number' && quote.totalPrice > 0;

  return {
    id: quote.id?.trim() || '',
    code: quote.code?.trim() || FIELD_PLACEHOLDER,
    proposalName: quote.name?.trim() || FIELD_PLACEHOLDER,
    company: quote.companyName?.trim() || FIELD_PLACEHOLDER,
    externalContact: resolveExternalContact(quote),
    createdAt: formatCreatedAt(quote.createdDate),
    estimatedValue: hasValue ? BRL_FORMATTER.format(quote.totalPrice) : undefined,
    status: mapStage(quote.stage ?? ''),
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
  private readonly appDialog = inject(AppDialogService);
  private readonly quotesService = inject(QuotesService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly filterTabs = FILTER_TABS;
  readonly activeFilter = signal<ChaveDaAba>('todos');
  readonly searchTerm = signal('');
  readonly loadError = signal(false);

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
      if (filter !== 'todos' && abaDaSituacao(orcamento.status) !== filter) return false;
      if (!term) return true;
      return (
        orcamento.code.toLowerCase().includes(term) ||
        orcamento.proposalName.toLowerCase().includes(term) ||
        orcamento.company.toLowerCase().includes(term) ||
        orcamento.externalContact.toLowerCase().includes(term)
      );
    });
  });

  constructor() {
    this.loadQuotes();
  }

  private loadQuotes(): void {
    this.loadError.set(false);
    this.quotesService
      .getQuotes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (quotes) => this.orcamentos.set(quotes.map(mapQuoteToOrcamento)),
        error: () => {
          this.orcamentos.set([]);
          this.loadError.set(true);
        },
      });
  }

  badgeFor(status: SituacaoDaSolicitacao): StatusBadge {
    return STATUS_BADGE[status];
  }

  setFilter(value: ChaveDaAba): void {
    this.activeFilter.set(value);
  }

  openStatusDialog(orcamento: Orcamento): void {
    const cancelado = ehTerminal(orcamento.status);
    const etapa: EtapaDaLinha = ehTerminal(orcamento.status) ? 'qualificacao' : orcamento.status;
    const indice = indiceDaEtapa(etapa);
    const grupo = grupoDaEtapa(indice);
    const aviso = cancelado ? undefined : AVISO_DA_ETAPA[etapa];

    const data: StatusPropostaDialogData = {
      proposalId: orcamento.code.replace(/^#/, ''),
      titulo: cancelado ? 'Solicitação encerrada' : grupo.titulo,
      // Cada tela mostra um recorte só, não a linha inteira.
      steps: PROPOSAL_STEPS.slice(grupo.de, grupo.ate + 1),
      currentIndex: indice - grupo.de,
      messageTitle: aviso?.titulo,
      message: cancelado
        ? 'Esta solicitação foi encerrada e não avança mais na linha de etapas.'
        : aviso?.texto,
      callToAction: aviso?.acao,
      protocolo: grupo.chave === 'proposta' ? undefined : `L-${orcamento.code}-X`,
      contato: grupo.chave === 'proposta' ? undefined : CONTATO_DO_LABORATORIO,
    };

    this.appDialog
      .open<StatusPropostaDialogComponent, StatusPropostaDialogData, string>(
        StatusPropostaDialogComponent,
        data,
        `${data.titulo} ${data.proposalId}`,
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resultado) => {
        if (resultado === 'confirmed') {
          void this.router.navigate(['/cliente/orcamentos', orcamento.id]);
        }
      });
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
  }
}
