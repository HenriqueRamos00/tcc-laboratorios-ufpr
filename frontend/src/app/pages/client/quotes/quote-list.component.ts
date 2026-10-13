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
  tabForStatus,
  type TabKey,
  isClosed,
  type PipelineStage,
  groupOfStage,
  stageIndex,
  PIPELINE_STAGES,
} from '@/app/model/analysis-pipeline';

import {
  badgeFor,
  type DisplayedQuote,
  type StatusBadge,
  toDisplayedQuote,
  withSessionDecision,
} from '@/app/model/quote-presentation';
import { noticeFor } from '@/app/model/quote-stage-notice';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';
import { QuotesService } from '@/app/services/quotes.service';

import {
  ProposalStatusDialogComponent,
  ProposalStatusDialogData,
} from './proposal-status-dialog/proposal-status-dialog.component';

interface FilterTab {
  value: TabKey;
  label: string;
}

const PROPOSAL_STEPS: ReadonlyArray<StatusStepperStep> = PIPELINE_STAGES.map((stage) => ({
  key: stage.key,
  label: stage.label,
  icon: stage.icon,
}));

// Placeholder de demonstração: o contato real vem do AutoLAB junto do
// protocolo, e não pode ser endereço de pessoa fixo no bundle.
const LABORATORY_CONTACT = 'atendimento@exemplo.com.br';

const FILTER_TABS: ReadonlyArray<FilterTab> = [
  { value: 'todos', label: 'Todos' },
  { value: 'pendentes', label: 'Pendentes de aceite' },
  { value: 'andamento', label: 'Em Andamento' },
  { value: 'finalizados', label: 'Finalizados' },
  { value: 'cancelados', label: 'Cancelados' },
];

function greetingForNow(agora = new Date()): string {
  const hora = agora.getHours();
  if (hora < 12) return 'Bom dia';
  if (hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

@Component({
  selector: 'app-client-quotes',
  standalone: true,
  imports: [NgClass, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './quote-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteListComponent {
  private readonly loginService = inject(LoginService);
  private readonly appDialog = inject(AppDialogService);
  private readonly quotesService = inject(QuotesService);
  private readonly decisions = inject(QuoteDecisionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly filterTabs = FILTER_TABS;
  readonly activeFilter = signal<TabKey>('todos');
  readonly searchTerm = signal('');
  readonly loadError = signal(false);

  readonly firstName = computed(() => {
    const name = this.loginService.currentUser();
    if (!name) return 'Cliente';
    return name.split(/\s+/)[0];
  });

  // Um computed aqui ficaria cacheado: a hora não é signal.
  readonly greeting = greetingForNow();

  readonly mobileFilterLabel = computed(() => {
    const current = this.activeFilter();
    if (current === 'todos') return 'Todos';
    return FILTER_TABS.find((tab) => tab.value === current)?.label ?? 'Todos';
  });

  private readonly loadedQuotes = signal<ReadonlyArray<DisplayedQuote>>([]);

  readonly quotes = computed(() => {
    const decided = this.decisions.decisions();
    return this.loadedQuotes().map((quote) =>
      withSessionDecision(quote, decided.get(quote.id)?.status),
    );
  });

  readonly visibleQuotes = computed(() => {
    const filter = this.activeFilter();
    const term = this.searchTerm().trim().toLowerCase();
    return this.quotes().filter((quote) => {
      if (filter !== 'todos' && tabForStatus(quote.status) !== filter) return false;
      if (!term) return true;
      return (
        quote.code.toLowerCase().includes(term) ||
        quote.proposalName.toLowerCase().includes(term) ||
        quote.company.toLowerCase().includes(term) ||
        quote.externalContact.toLowerCase().includes(term)
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
        next: (quotes) => this.loadedQuotes.set(quotes.map(toDisplayedQuote)),
        error: () => {
          this.loadedQuotes.set([]);
          this.loadError.set(true);
        },
      });
  }

  badgeFor(status: DisplayedQuote['status']): StatusBadge {
    return badgeFor(status);
  }

  setFilter(value: TabKey): void {
    this.activeFilter.set(value);
  }

  // Com o laudo publicado não há mais etapa a acompanhar: o que o cliente quer
  // ver é o relatório, então o botão leva direto para os laudos do orçamento.
  checkStatus(quote: DisplayedQuote): void {
    if (quote.status === 'relatorio-publicado') {
      void this.router.navigate(['/cliente/relatorios', quote.id]);
      return;
    }

    this.openStatusDialog(quote);
  }

  private openStatusDialog(quote: DisplayedQuote): void {
    const closed = isClosed(quote.status);
    const stage: PipelineStage = closed ? 'qualificacao' : quote.status;
    const index = stageIndex(stage);
    const group = groupOfStage(index);
    const notice = closed ? undefined : noticeFor(stage);

    const data: ProposalStatusDialogData = {
      proposalId: quote.code.replace(/^#/, ''),
      quoteId: quote.id,
      hasProposal: quote.hasProposal,
      title: closed ? 'Solicitação encerrada' : group.title,
      // A linha é uma só, de qualificação a relatório publicado: o modal
      // entrega a linha inteira e a etapa atual por índice absoluto, e quem
      // escolhe o que cabe na tela é o stepper, que mantém a etapa atual no
      // meio da janela. Recortar por grupo aqui jogava a etapa atual para a
      // borda e escondia as que vêm logo depois dela.
      steps: PROPOSAL_STEPS,
      currentIndex: index,
      messageTitle: notice?.title,
      message: closed
        ? 'Esta solicitação foi encerrada e não avança mais na linha de etapas.'
        : notice?.text,
      callToAction: notice?.callToAction,
      shipping: notice?.shipping,
      protocolo: group.key === 'proposta' ? undefined : `L-${quote.code}-X`,
      contato: group.key === 'proposta' ? undefined : LABORATORY_CONTACT,
    };

    this.appDialog
      .open<ProposalStatusDialogComponent, ProposalStatusDialogData, string>(
        ProposalStatusDialogComponent,
        data,
        `${data.title} ${data.proposalId}`,
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resultado) => {
        if (resultado === 'confirmed') {
          void this.router.navigate(['/cliente/quotes', quote.id]);
        }
      });
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
  }
}
