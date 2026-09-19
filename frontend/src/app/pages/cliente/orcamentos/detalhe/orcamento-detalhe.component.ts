import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import type { FormaDeAprovacao } from '@/app/model/acceptance';
import type { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';
import { QuotesService } from '@/app/services/quotes.service';

import { AprovarOrcamentoDialogComponent } from '../decisao/aprovar-orcamento-dialog.component';
import { OrdemDeCompraDialogComponent } from '../decisao/ordem-de-compra-dialog.component';
import {
  RecusarOrcamentoDialogComponent,
  type RecusarOrcamentoDialogData,
} from '../decisao/recusar-orcamento-dialog.component';

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
  imports: [MatIconModule, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './orcamento-detalhe.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrcamentoDetalheComponent {
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

  /** Já decidido nesta sessão: some com os botões de ação (HU-09, critério 6). */
  protected readonly jaDecidido = computed(() => !!this.decisao.decisoes().get(this.id()));

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
      .open<AprovarOrcamentoDialogComponent, undefined, FormaDeAprovacao>(
        AprovarOrcamentoDialogComponent,
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
    const dados: RecusarOrcamentoDialogData = {
      nomeDaProposta: this.orcamento()?.name ?? this.id(),
    };
    // O nome acessível é a mesma frase que a tela mostra; um rótulo paralelo
    // faz o leitor de tela anunciar coisa diferente do que está escrito.
    const rotulo = `Deseja realmente recusar o orçamento "${dados.nomeDaProposta}"?`;
    this.dialogs
      .open<RecusarOrcamentoDialogComponent, RecusarOrcamentoDialogData, { justificativa: string }>(
        RecusarOrcamentoDialogComponent,
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
      .open<OrdemDeCompraDialogComponent, undefined, File>(
        OrdemDeCompraDialogComponent,
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
