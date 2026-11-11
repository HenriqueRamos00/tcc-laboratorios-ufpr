import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

export interface StatusJustificativaDialogData {
  acao: 'rejeitar' | 'cancelar';
}

@Component({
  selector: 'app-status-justificativa-dialog',
  standalone: true,
  imports: [
    MatButtonModule,
    MatDialogModule,
    ReactiveFormsModule,
    DialogShellComponent,
  ],
  templateUrl: './status-justificativa-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusJustificativaDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<StatusJustificativaDialogComponent, string | undefined>);
  readonly data = inject<StatusJustificativaDialogData>(MAT_DIALOG_DATA);
  readonly justificativa = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });

  get titulo(): string {
    return this.data.acao === 'cancelar' ? 'Cancelar ensaio' : 'Rejeitar ensaio';
  }

  get textoConfirmacao(): string {
    return this.data.acao === 'cancelar'
      ? 'Confirmar cancelamento'
      : 'Confirmar rejeição';
  }

  confirmar(): void {
    const valor = this.justificativa.value.trim();
    if (!valor) {
      this.justificativa.markAsTouched();
      return;
    }
    this.dialogRef.close(valor);
  }
}
