import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';

import type { FormaDeAprovacao } from '@/app/model/acceptance';
import {
  ehTerminal,
  indiceDaEtapa,
  type SituacaoDaSolicitacao,
} from '@/app/model/analysis-pipeline';
import type { Quote } from '@/app/model/quote';
import { recognizeStage } from '@/app/model/quote-presentation';
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

function saudacaoDoDia(agora = new Date()): string {
  const hora = agora.getHours();
  if (hora < 12) return 'Bom dia';
  if (hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

@Component({
  selector: 'app-orcamento-detalhe',
  standalone: true,
  imports: [
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    SampleShippingNoticeComponent,
  ],
  templateUrl: './quote-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteDetailComponent {
  readonly id = input.required<string>();

  private readonly quotes = inject(QuotesService);
  private readonly decisao = inject(QuoteDecisionService);
  private readonly dialogs = inject(AppDialogService);
  private readonly loginService = inject(LoginService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly orcamento = signal<Quote | null>(null);
  protected readonly falhou = signal(false);
  protected readonly enviando = signal(false);

  // Um computed aqui ficaria cacheado: a hora não é signal.
  protected readonly saudacao = saudacaoDoDia();

  protected readonly primeiroNome = computed(
    () => this.loginService.currentUser()?.split(/\s+/)[0] ?? 'Cliente',
  );

  protected readonly total = computed(() => {
    const valor = this.orcamento()?.totalPrice;
    return typeof valor === 'number' ? BRL.format(valor) : '—';
  });

  // A decisão tomada nesta sessão vale mais que a etapa que veio na carga da
  // tela: o Salesforce ainda não sabe do aceite que acabou de acontecer aqui.
  protected readonly stage = computed<SituacaoDaSolicitacao | null>(() => {
    const decidido = this.decisao.decisoes().get(this.id());
    if (decidido) return decidido.situacao;

    const quote = this.orcamento();
    return quote ? recognizeStage(quote.stage) : null;
  });

  protected readonly pendingAction = computed<PendingAction>(() => {
    const stage = this.stage();
    if (!stage || ehTerminal(stage)) return 'follow';
    if (stage === 'aprovado-pelo-cliente') return 'confirm-shipment';
    return indiceDaEtapa(stage) < indiceDaEtapa('aprovado-pelo-cliente') ? 'decide' : 'follow';
  });

  protected readonly subtitle = computed(() => SUBTITLE[this.pendingAction()]);

  protected readonly followMessage = computed(() => {
    const stage = this.stage();
    return stage && ehTerminal(stage)
      ? 'Esta proposta foi encerrada e não avança mais na linha de etapas.'
      : 'Não há nada pendente de sua parte neste orçamento.';
  });

  constructor() {
    // O router reaproveita a instância quando só o id muda, e ngOnInit não roda
    // de novo.
    toObservable(this.id)
      .pipe(
        tap(() => {
          this.orcamento.set(null);
          this.falhou.set(false);
        }),
        switchMap((id) =>
          this.quotes.getQuoteById(id).pipe(
            catchError(() => {
              this.falhou.set(true);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((quote) => this.orcamento.set(quote));
  }

  protected aceitar(): void {
    this.dialogs
      .open<ApproveQuoteDialogComponent, undefined, FormaDeAprovacao>(
        ApproveQuoteDialogComponent,
        undefined,
        'Como deseja aprovar o orçamento',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((forma) => {
        if (forma === 'termo-de-aceite') {
          void this.router.navigate(['/cliente/orcamentos', this.id(), 'aceite']);
        } else if (forma === 'ordem-de-compra') {
          this.anexarOrdemDeCompra();
        }
      });
  }

  protected confirmarEnvioDaAmostra(): void {
    this.enviando.set(true);
    this.decisao
      .confirmarEnvioDaAmostra(this.id())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.avisar('Envio da amostra confirmado');
          void this.router.navigate(['/cliente/orcamentos']);
        },
        error: () => {
          this.enviando.set(false);
          this.avisar('Não foi possível confirmar o envio da amostra no momento');
        },
      });
  }

  protected recusar(): void {
    const dados: RejectQuoteDialogData = {
      nomeDaProposta: this.orcamento()?.name ?? this.id(),
    };
    // O nome acessível é a mesma frase que a tela mostra; um rótulo paralelo
    // faz o leitor de tela anunciar coisa diferente do que está escrito.
    const rotulo = `Deseja realmente recusar o orçamento "${dados.nomeDaProposta}"?`;
    this.dialogs
      .open<RejectQuoteDialogComponent, RejectQuoteDialogData, { justificativa: string }>(
        RejectQuoteDialogComponent,
        dados,
        rotulo,
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resposta) => {
        if (!resposta) return;
        this.enviando.set(true);
        this.decisao
          .recusar({ propostaId: this.id(), justificativa: resposta.justificativa })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.enviando.set(false);
              this.avisar('Orçamento recusado com sucesso');
              void this.router.navigate(['/cliente/orcamentos']);
            },
            error: () => {
              this.enviando.set(false);
              this.avisar('Não foi possível recusar o orçamento no momento');
            },
          });
      });
  }

  private anexarOrdemDeCompra(): void {
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
        this.enviando.set(true);
        this.decisao
          .aceitarComOrdemDeCompra(this.id(), arquivo)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.enviando.set(false);
              this.avisar('Aceite enviado com sucesso');
              void this.router.navigate(['/cliente/orcamentos']);
            },
            error: () => {
              this.enviando.set(false);
              this.avisar('Não foi possível enviar o aceite no momento');
            },
          });
      });
  }

  private avisar(mensagem: string): void {
    this.snackBar.open(mensagem, 'Fechar', { duration: 5000 });
  }
}
