import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';

export interface ConfirmAcceptanceDialogData {
  readonly proposalCode: string;
}

// Enviar o aceite põe o orçamento em execução, e daí em diante corrigir um dado
// do termo passa a depender de contato com a equipe. A parada antes do envio é
// o último momento barato para revisar, e é também o último momento em que o
// lembrete da etiqueta ainda alcança a caixa que vai sair da empresa.
@Component({
  selector: 'app-confirm-acceptance-dialog',
  standalone: true,
  imports: [DialogShellComponent, SampleShippingNoticeComponent],
  template: `
    <app-dialog-shell
      title="Confirmar envio do aceite"
      subtitle="Confira se todas as informações do termo estão corretas antes de enviar."
    >
      <app-sample-shipping-notice [proposalCode]="data.proposalCode" />

      <div dialog-footer class="flex w-full flex-col gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          class="rounded-lg bg-lactec-line px-5 py-3 text-sm font-semibold text-lactec-ink
                 hover:bg-lactec-divider"
          (click)="keepEditing()"
        >
          Continuar editando
        </button>
        <button
          type="button"
          class="rounded-lg bg-lactec-success px-5 py-3 text-sm font-semibold
                 text-lactec-on-primary hover:opacity-90"
          (click)="confirm()"
        >
          Confirmar envio
        </button>
      </div>
    </app-dialog-shell>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmAcceptanceDialogComponent {
  protected readonly data = inject<ConfirmAcceptanceDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmAcceptanceDialogComponent, boolean>);

  protected confirm(): void {
    this.dialogRef.close(true);
  }

  protected keepEditing(): void {
    this.dialogRef.close(false);
  }
}
