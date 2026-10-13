import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';
import { handFileToTheBrowser } from '@shared/file-download';
import {
  StatusStepperComponent,
  StatusStepperStep,
} from '@shared/components/status-stepper/status-stepper.component';

import type { ShippingGuidance } from '@/app/model/sample-shipping';
import { QuotesService } from '@/app/services/quotes.service';

export interface ProposalStatusDialogData {
  /** Código da proposta, o que o cliente lê e informa ao laboratório. */
  proposalId: string;
  /** Identificador do orçamento no Salesforce, o que a API entende. */
  quoteId: string;
  /** Título do recorte: proposta, análise técnica das amostras ou laboratório. */
  titulo: string;
  steps: readonly StatusStepperStep[];
  currentIndex: number;
  messageTitle?: string;
  message?: string;
  callToAction?: string;
  /** Falso enquanto ninguém escreveu a proposta: não há orçamento a abrir nem a baixar. */
  hasProposal: boolean;
  /** Orientação de envio da amostra, nas etapas em que o cliente despacha o material. */
  shipping?: ShippingGuidance;
  /** Protocolo do laboratório, exibido a partir do recebimento da amostra. */
  protocolo?: string;
  /** Contato do responsável, exibido nos recortes de laboratório. */
  contato?: string;
}

type CopyState = 'idle' | 'copied' | 'failed';

const COPY_FEEDBACK_MILLISECONDS = 2000;

const DOWNLOAD_FAILURE = 'O PDF desta proposta ainda não está disponível.';

@Component({
  selector: 'app-status-proposta-dialog',
  standalone: true,
  imports: [
    DialogShellComponent,
    StatusStepperComponent,
    SampleShippingNoticeComponent,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './proposal-status-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProposalStatusDialogComponent {
  readonly data = inject<ProposalStatusDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ProposalStatusDialogComponent>);
  private readonly clipboard = inject(Clipboard);
  private readonly quotes = inject(QuotesService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly copyState = signal<CopyState>('idle');
  protected readonly downloading = signal(false);
  protected readonly downloadFailure = signal('');

  confirm(): void {
    this.dialogRef.close('confirmed');
  }

  // A área de transferência não existe em contexto inseguro nem em todo
  // navegador, então o endereço continua visível e selecionável na caixa: o
  // botão é atalho, e não o único caminho para copiar.
  protected copyContact(): void {
    const contato = this.data.contato;
    if (!contato) return;

    this.copyState.set(this.clipboard.copy(contato) ? 'copied' : 'failed');
    setTimeout(() => this.copyState.set('idle'), COPY_FEEDBACK_MILLISECONDS);
  }

  protected downloadQuote(): void {
    this.downloading.set(true);
    this.downloadFailure.set('');

    this.quotes
      .quoteDocument(this.data.quoteId)
      .pipe(
        finalize(() => this.downloading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (file) => handFileToTheBrowser(file, `orcamento-${this.data.proposalId}.pdf`),
        error: () => this.downloadFailure.set(DOWNLOAD_FAILURE),
      });
  }
}
