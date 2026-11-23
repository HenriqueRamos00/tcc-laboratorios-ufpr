import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';

import { OrcamentoDetalhe, OrcamentoItem } from '@/app/model/orcamento';
import {
  classeStatusOrcamento,
  rotuloFormaAceiteOrcamento,
  rotuloStatusOrcamento,
} from '@/app/model/orcamento-presentation';
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
  readonly carregandoOrdemCompra = signal(false);
  readonly erroOrdemCompra = signal<string | null>(null);
  readonly rotuloStatus = rotuloStatusOrcamento;
  readonly classeStatus = classeStatusOrcamento;
  readonly rotuloFormaAceite = rotuloFormaAceiteOrcamento;

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

  abrirOrdemCompra(): void {
    if (this.carregandoOrdemCompra()) return;

    const janela = window.open('about:blank', '_blank');
    this.erroOrdemCompra.set(null);
    if (!janela) {
      this.erroOrdemCompra.set('O navegador bloqueou a abertura do arquivo. Permita pop-ups para este portal.');
      return;
    }
    janela.opener = null;
    this.carregandoOrdemCompra.set(true);

    this.orcamentosService.downloadPurchaseOrder(this.data.quoteId).pipe(
      tap((resposta) => this.abrirArquivoOrdemCompra(resposta, janela)),
      catchError((error: HttpErrorResponse) => {
        janela.close();
        this.erroOrdemCompra.set(error.status === 404
          ? 'O arquivo da ordem de compra não está mais disponível.'
          : error.status === 403
            ? 'Sua conta não tem permissão para consultar este arquivo.'
            : 'Não foi possível abrir a ordem de compra. Tente novamente.');
        return EMPTY;
      }),
      finalize(() => this.carregandoOrdemCompra.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private abrirArquivoOrdemCompra(resposta: HttpResponse<Blob>, janela: Window): void {
    const arquivo = resposta.body;
    const tipo = (resposta.headers.get('Content-Type') ?? arquivo?.type ?? '')
      .split(';')[0]
      .trim()
      .toLowerCase();
    const tiposPermitidos = [
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    ];

    if (!arquivo?.size || !tiposPermitidos.includes(tipo)) {
      janela.close();
      this.erroOrdemCompra.set('A API não retornou um arquivo de ordem de compra válido.');
      return;
    }

    const url = URL.createObjectURL(new Blob([arquivo], { type: tipo }));
    if (tipo === 'application/pdf') {
      janela.location.href = url;
    } else {
      janela.close();
      const link = document.createElement('a');
      link.href = url;
      link.download = this.nomeArquivoOrdemCompra(resposta, tipo);
      link.hidden = true;
      document.body.appendChild(link);
      link.click();
      link.remove();
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  private nomeArquivoOrdemCompra(resposta: HttpResponse<Blob>, tipo: string): string {
    const contentDisposition = resposta.headers.get('Content-Disposition') ?? '';
    const nomeCodificado = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const nomeSimples = contentDisposition.match(/filename="?([^";]+)"?/i)?.[1];
    const nomeOriginal = nomeCodificado ?? nomeSimples;
    if (nomeOriginal) {
      try {
        return decodeURIComponent(nomeOriginal.replace(/^"|"$/g, ''));
      } catch {
        return nomeOriginal.replace(/^"|"$/g, '');
      }
    }

    const extensao = tipo === 'application/msword' ? 'doc' : 'docx';
    return `ordem-de-compra.${extensao}`;
  }
}
