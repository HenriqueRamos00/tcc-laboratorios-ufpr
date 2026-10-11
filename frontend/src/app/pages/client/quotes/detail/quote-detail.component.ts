import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import type { Quote } from '@/app/model/quote';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

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
  imports: [PageHeaderComponent, EmptyStateComponent, MatButtonModule],
  templateUrl: './quote-detail.component.html',
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
}
