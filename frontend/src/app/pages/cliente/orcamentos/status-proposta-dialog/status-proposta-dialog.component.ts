import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import {
  StatusStepperComponent,
  StatusStepperStep,
} from '@shared/components/status-stepper/status-stepper.component';

export interface StatusPropostaDialogData {
  proposalId: string;
  /** Título do recorte: proposta, análise técnica das amostras ou laboratório. */
  titulo: string;
  steps: readonly StatusStepperStep[];
  currentIndex: number;
  messageTitle?: string;
  message?: string;
  callToAction?: string;
  /** Protocolo do laboratório, exibido a partir do recebimento da amostra. */
  protocolo?: string;
  /** Contato do responsável, exibido nos recortes de laboratório. */
  contato?: string;
}

@Component({
  selector: 'app-status-proposta-dialog',
  standalone: true,
  imports: [DialogShellComponent, StatusStepperComponent, MatButtonModule, MatIconModule],
  templateUrl: './status-proposta-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusPropostaDialogComponent {
  readonly data = inject<StatusPropostaDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<StatusPropostaDialogComponent>);

  confirm(): void {
    this.dialogRef.close('confirmed');
  }
}
