import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, finalize, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';
import { handFileToTheBrowser } from '@shared/file-download';

import type { ApprovalMethod } from '@/app/model/acceptance';
import {
  isClosed,
  stageIndex,
  type RequestStatus,
} from '@/app/model/analysis-pipeline';
import type { Quote } from '@/app/model/quote';
import { hasProposal, recognizeStage } from '@/app/model/quote-presentation';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';
import { QuotesService } from '@/app/services/quotes.service';

import { ApproveQuoteDialogComponent } from '../decision/approve-quote-dialog.component';
import { PurchaseOrderDialogComponent } from '../decision/purchase-order-dialog.component';
import {
  RejectQuoteDialogComponent,
  type RejectQuoteDialogData,
} from '../decision/reject-quote-dialog.component';

import { ProposalViewerComponent } from './proposal-viewer.component';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/**
 * A tela oferece uma ação só, e quem a escolhe é a etapa: em negociação o
 * cliente aprova ou recusa; depois de aprovado o que falta dele é despachar a
 * amostra e dizer que despachou; do recebimento em diante não há nada a fazer
 * aqui, só acompanhar.
 */
type PendingAction = 'decide' | 'confirm-shipment' | 'follow';

const SUBTITLE: Record<PendingAction, string> = {
  decide: 'Aprove ou recuse as propostas de orçamento.',
  'confirm-shipment': 'Confirme o envio da amostra para o laboratório.',
  follow: 'Acompanhe a situação desta proposta.',
};

function greetingForNow(agora = new Date()): string {
  const hora = agora.getHours();
  if (hora < 12) return 'Bom dia';
  if (hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

@Component({
  selector: 'app-quote-detalhe',
  standalone: true,
  imports: [
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ProposalViewerComponent,
    SampleShippingNoticeComponent,
  ],
  templateUrl: './quote-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteDetailComponent {
  readonly id = input.required<string>();

  private readonly quotes = inject(QuotesService);
  private readonly decisions = inject(QuoteDecisionService);
  private readonly dialogs = inject(AppDialogService);
  private readonly loginService = inject(LoginService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly quote = signal<Quote | null>(null);
  protected readonly loadFailed = signal(false);
  protected readonly sending = signal(false);
  protected readonly downloading = signal(false);

  // Um computed aqui ficaria cacheado: a hora não é signal.
  protected readonly greeting = greetingForNow();

  protected readonly firstName = computed(
    () => this.loginService.currentUser()?.split(/\s+/)[0] ?? 'Cliente',
  );

  protected readonly total = computed(() => {
    const valor = this.quote()?.totalPrice;
    return typeof valor === 'number' ? BRL.format(valor) : '—';
  });

  // A decisão tomada nesta sessão vale mais que a etapa que veio na carga da
  // tela: o Salesforce ainda não sabe do aceite que acabou de acontecer aqui.
  protected readonly stage = computed<RequestStatus | null>(() => {
    const decidido = this.decisions.decisions().get(this.id());
    if (decidido) return decidido.status;

    const quote = this.quote();
    return quote ? recognizeStage(quote.stage) : null;
  });

  protected readonly pendingAction = computed<PendingAction>(() => {
    const stage = this.stage();
    if (!stage || isClosed(stage) || !hasProposal(stage)) return 'follow';
    if (stage === 'aprovado-pelo-cliente') return 'confirm-shipment';
    return stageIndex(stage) < stageIndex('aprovado-pelo-cliente') ? 'decide' : 'follow';
  });

  protected readonly subtitle = computed(() => SUBTITLE[this.pendingAction()]);

  // Antes da negociação ninguém terminou de escrever a proposta: não existe
  // documento para exibir, e um visualizador vazio afirmaria que existe.
  protected readonly proposalAvailable = computed(() => {
    const stage = this.stage();
    return stage !== null && hasProposal(stage);
  });

  // O nome do arquivo sai de um lugar só: o botão desta página e o botão de
  // download da barra do visualizador salvam o mesmo documento, e dois nomes
  // diferentes para o mesmo PDF fazem o cliente achar que recebeu dois
  // orçamentos.
  protected readonly proposalFileName = computed(
    () => `proposta-${this.quote()?.code ?? this.id()}.pdf`,
  );

  // Array literal no template cria identidade nova a cada ciclo e suja o
  // PageHeader, que é OnPush, sem nada ter mudado.
  protected readonly breadcrumbs = computed(() => [
    { label: 'Orçamentos', route: '/cliente/quotes' },
    { label: this.quote()?.code ?? this.id() },
  ]);

  protected readonly followMessage = computed(() => {
    const stage = this.stage();
    if (stage && isClosed(stage)) {
      return 'Esta proposta foi encerrada e não avança mais na linha de etapas.';
    }
    if (stage && !hasProposal(stage)) {
      return 'Enquanto a proposta é preparada, não há nada pendente de sua parte.';
    }
    return 'Não há nada pendente de sua parte neste orçamento.';
  });

  constructor() {
    // O router reaproveita a instância quando só o id muda, e ngOnInit não roda
    // de novo.
    toObservable(this.id)
      .pipe(
        tap(() => {
          this.quote.set(null);
          this.loadFailed.set(false);
        }),
        switchMap((id) =>
          this.quotes.getQuoteById(id).pipe(
            catchError(() => {
              this.loadFailed.set(true);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((quote) => this.quote.set(quote));
  }

  protected aceitar(): void {
    this.dialogs
      .open<ApproveQuoteDialogComponent, undefined, ApprovalMethod>(
        ApproveQuoteDialogComponent,
        undefined,
        'Como deseja aprovar o orçamento',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((forma) => {
        if (forma === 'termo-de-aceite') {
          void this.router.navigate(['/cliente/quotes', this.id(), 'aceite']);
        } else if (forma === 'ordem-de-compra') {
          this.attachPurchaseOrder();
        }
      });
  }

  protected confirmSampleShipment(): void {
    this.sending.set(true);
    this.decisions
      .confirmSampleShipment(this.id())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.notify('Envio da amostra confirmado');
          void this.router.navigate(['/cliente/quotes']);
        },
        error: () => {
          this.sending.set(false);
          this.notify('Não foi possível confirmar o envio da amostra no momento');
        },
      });
  }

  protected recusar(): void {
    const data: RejectQuoteDialogData = {
      proposalName: this.quote()?.name ?? this.id(),
    };
    // O nome acessível é a mesma frase que a tela mostra; um rótulo paralelo
    // faz o leitor de tela anunciar coisa diferente do que está escrito.
    const label = `Deseja realmente recusar o orçamento "${data.proposalName}"?`;
    this.dialogs
      .open<RejectQuoteDialogComponent, RejectQuoteDialogData, { justification: string }>(
        RejectQuoteDialogComponent,
        data,
        label,
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resposta) => {
        if (!resposta) return;
        this.sending.set(true);
        this.decisions
          .recusar({ proposalId: this.id(), justification: resposta.justification })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.sending.set(false);
              this.notify('Orçamento recusado com sucesso');
              void this.router.navigate(['/cliente/quotes']);
            },
            error: () => {
              this.sending.set(false);
              this.notify('Não foi possível recusar o orçamento no momento');
            },
          });
      });
  }

  protected downloadProposal(): void {
    this.downloading.set(true);
    this.quotes
      .quoteDocument(this.id())
      .pipe(
        finalize(() => this.downloading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (file) => handFileToTheBrowser(file, this.proposalFileName()),
        error: () => this.notify('Não foi possível baixar o PDF da proposta no momento'),
      });
  }

  private attachPurchaseOrder(): void {
    this.dialogs
      .open<PurchaseOrderDialogComponent, undefined, File>(
        PurchaseOrderDialogComponent,
        undefined,
        'Anexar ordem de compra',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((arquivo) => {
        if (!arquivo) return;
        this.sending.set(true);
        this.decisions
          .aceitarComOrdemDeCompra(this.id(), arquivo)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.sending.set(false);
              this.notify('Aceite enviado com sucesso');
              void this.router.navigate(['/cliente/quotes']);
            },
            error: () => {
              this.sending.set(false);
              this.notify('Não foi possível enviar o aceite no momento');
            },
          });
      });
  }

  private notify(mensagem: string): void {
    this.snackBar.open(mensagem, 'Fechar', { duration: 5000 });
  }
}
