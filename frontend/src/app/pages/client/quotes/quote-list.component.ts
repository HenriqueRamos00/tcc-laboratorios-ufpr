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
} from '@/app/model/analysis-pipeline';

import {
  badgeFor,
  type DisplayedQuote,
  type StatusBadge,
  toDisplayedQuote,
} from '@/app/model/quote-presentation';
import { noticeFor } from '@/app/model/quote-stage-notice';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

import {
  ProposalStatusDialogComponent,
  ProposalStatusDialogData,
} from './proposal-status-dialog/proposal-status-dialog.component';

interface FilterTab {
  value: ChaveDaAba;
  label: string;
}

const PROPOSAL_STEPS: ReadonlyArray<StatusStepperStep> = LINHA_DE_ETAPAS.map((etapa) => ({
  key: etapa.chave,
  label: etapa.rotulo,
  icon: etapa.icone,
}));

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

function saudacaoDoDia(agora = new Date()): string {
  const hora = agora.getHours();
  if (hora < 12) return 'Bom dia';
  if (hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

@Component({
  selector: 'app-client-orcamentos',
  standalone: true,
  imports: [NgClass, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './quote-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteListComponent {
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

  readonly quotes = signal<ReadonlyArray<DisplayedQuote>>([]);

  readonly visibleQuotes = computed(() => {
    const filter = this.activeFilter();
    const term = this.searchTerm().trim().toLowerCase();
    return this.quotes().filter((quote) => {
      if (filter !== 'todos' && abaDaSituacao(quote.status) !== filter) return false;
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
        next: (quotes) => this.quotes.set(quotes.map(toDisplayedQuote)),
        error: () => {
          this.quotes.set([]);
          this.loadError.set(true);
        },
      });
  }

  badgeFor(status: DisplayedQuote['status']): StatusBadge {
    return badgeFor(status);
  }

  setFilter(value: ChaveDaAba): void {
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
    const encerrado = ehTerminal(quote.status);
    const etapa: EtapaDaLinha = encerrado ? 'qualificacao' : quote.status;
    const indice = indiceDaEtapa(etapa);
    const grupo = grupoDaEtapa(indice);
    const aviso = encerrado ? undefined : noticeFor(etapa);

    const data: ProposalStatusDialogData = {
      proposalId: quote.code.replace(/^#/, ''),
      quoteId: quote.id,
      hasProposal: quote.hasProposal,
      titulo: encerrado ? 'Solicitação encerrada' : grupo.titulo,
      // A linha é uma só, de qualificação a relatório publicado: o modal
      // entrega a linha inteira e a etapa atual por índice absoluto, e quem
      // escolhe o que cabe na tela é o stepper, que mantém a etapa atual no
      // meio da janela. Recortar por grupo aqui jogava a etapa atual para a
      // borda e escondia as que vêm logo depois dela.
      steps: PROPOSAL_STEPS,
      currentIndex: indice,
      messageTitle: aviso?.title,
      message: encerrado
        ? 'Esta solicitação foi encerrada e não avança mais na linha de etapas.'
        : aviso?.text,
      callToAction: aviso?.callToAction,
      shipping: aviso?.shipping,
      protocolo: grupo.chave === 'proposta' ? undefined : `L-${quote.code}-X`,
      contato: grupo.chave === 'proposta' ? undefined : CONTATO_DO_LABORATORIO,
    };

    this.appDialog
      .open<ProposalStatusDialogComponent, ProposalStatusDialogData, string>(
        ProposalStatusDialogComponent,
        data,
        `${data.titulo} ${data.proposalId}`,
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resultado) => {
        if (resultado === 'confirmed') {
          void this.router.navigate(['/cliente/orcamentos', quote.id]);
        }
      });
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
  }
}
