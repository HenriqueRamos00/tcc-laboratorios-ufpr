import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

export interface ConfirmacaoDialogData {
  titulo: string;
  mensagem: string;
  textoConfirmar?: string;
  textoCancelar?: string;
  perigo?: boolean;
}

@Component({
  selector: 'app-confirmacao-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, DialogShellComponent],
  templateUrl: './confirmacao-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmacaoDialogComponent {
  readonly data = inject<ConfirmacaoDialogData>(MAT_DIALOG_DATA);
}
