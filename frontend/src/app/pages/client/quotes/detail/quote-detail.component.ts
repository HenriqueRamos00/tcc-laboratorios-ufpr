import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import type { Quote } from '@/app/model/quote';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

<<<<<<< HEAD:frontend/src/app/pages/cliente/orcamentos/detalhe/orcamento-detalhe.component.ts
=======
import { ApproveQuoteDialogComponent } from '../decision/approve-quote-dialog.component';
import { PurchaseOrderDialogComponent } from '../decision/purchase-order-dialog.component';
import {
  RejectQuoteDialogComponent,
  type RejectQuoteDialogData,
} from '../decision/reject-quote-dialog.component';

>>>>>>> 5702f00 (FET: Inicio da arquitetura para consumo do autolab):frontend/src/app/pages/client/quotes/detail/quote-detail.component.ts
const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

function saudacaoDoDia(agora = new Date()): string {
  const hora = agora.getHours();
  if (hora < 12) return 'Bom dia';
  if (hora < 18) return 'Boa tarde';
  return 'Boa noite';
}

@Component({
  selector: 'app-orcamento-detalhe',
  standalone: true,
<<<<<<< HEAD:frontend/src/app/pages/cliente/orcamentos/detalhe/orcamento-detalhe.component.ts
  imports: [PageHeaderComponent, EmptyStateComponent, MatButtonModule],
  templateUrl: './orcamento-detalhe.component.html',
=======
  imports: [MatIconModule, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './quote-detail.component.html',
>>>>>>> 5702f00 (FET: Inicio da arquitetura para consumo do autolab):frontend/src/app/pages/client/quotes/detail/quote-detail.component.ts
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteDetailComponent {
  readonly id = input.required<string>();

  private readonly quotes = inject(QuotesService);
  private readonly loginService = inject(LoginService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly orcamento = signal<Quote | null>(null);
  protected readonly falhou = signal(false);
  protected readonly baixandoPdf = signal(false);
  protected readonly falhaNoPdf = signal(false);
  protected readonly saudacao = saudacaoDoDia();
  protected readonly primeiroNome = computed(
    () => this.loginService.currentUser()?.split(/\s+/)[0] ?? 'Cliente',
  );
  protected readonly total = computed(() => {
    const valor = this.orcamento()?.totalPrice;
    return typeof valor === 'number' ? BRL.format(valor) : '-';
  });

  protected baixarPdf(): void {
    const quote = this.orcamento();
    if (!quote || this.baixandoPdf()) return;

    this.baixandoPdf.set(true);
    this.falhaNoPdf.set(false);
    this.quotes.getQuoteDocument(quote.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (documento) => {
        const url = URL.createObjectURL(documento);
        const link = document.createElement('a');
        const codigo = quote.code.trim().replace(/[^a-z0-9_-]/gi, '_') || 'orcamento';
        link.href = url;
        link.download = `${codigo}.pdf`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.baixandoPdf.set(false);
      },
      error: () => {
        this.baixandoPdf.set(false);
        this.falhaNoPdf.set(true);
      },
    });
  }

  constructor() {
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
<<<<<<< HEAD:frontend/src/app/pages/cliente/orcamentos/detalhe/orcamento-detalhe.component.ts
=======

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
>>>>>>> 5702f00 (FET: Inicio da arquitetura para consumo do autolab):frontend/src/app/pages/client/quotes/detail/quote-detail.component.ts
}
