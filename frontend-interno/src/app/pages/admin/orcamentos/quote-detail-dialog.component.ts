import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';

import { OrcamentoDetalhe, OrcamentoItem } from '@/app/model/orcamento';
import { classeStatusOrcamento, rotuloStatusOrcamento } from '@/app/model/orcamento-presentation';
import { OrcamentosService } from '@/app/services/orcamentos.service';
import { DialogShellComponent } from '@/app/shared/components/dialog-shell/dialog-shell.component';
import { calcularSubtotalEmCentavos, converterCentavosParaValorSeguro } from './orcamento-money';

export interface QuoteDetailDialogData {
  readonly quoteId: string;
  readonly quoteCode: string;
}

@Component({
  selector: 'app-quote-detail-dialog',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    DialogShellComponent,
    MatDialogModule,
    MatProgressSpinnerModule,
    NgClass,
  ],
  templateUrl: './quote-detail-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteDetailDialogComponent {
  private readonly orcamentosService = inject(OrcamentosService);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<QuoteDetailDialogData>(MAT_DIALOG_DATA);
  readonly orcamento = signal<OrcamentoDetalhe | null>(null);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly erroNaoEncontrado = signal(false);
  readonly rotuloStatus = rotuloStatusOrcamento;
  readonly classeStatus = classeStatusOrcamento;

  subtotalItem(item: OrcamentoItem): number | null {
    const centavos = calcularSubtotalEmCentavos(item.sampleCount, item.unitPrice);
    return centavos === null ? null : converterCentavosParaValorSeguro(centavos);
  }

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.erroPermissao.set(false);
    this.erroNaoEncontrado.set(false);

    this.orcamentosService.get(this.data.quoteId).pipe(
      tap((detalhe) => this.orcamento.set(detalhe)),
      catchError((error: HttpErrorResponse) => {
        this.erro.set(true);
        this.erroPermissao.set(error.status === 403);
        this.erroNaoEncontrado.set(error.status === 404);
        return EMPTY;
      }),
      finalize(() => this.carregando.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }
}
