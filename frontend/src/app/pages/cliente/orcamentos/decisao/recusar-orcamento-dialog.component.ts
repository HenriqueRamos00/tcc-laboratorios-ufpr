import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

export interface RecusarOrcamentoDialogData {
  readonly nomeDaProposta: string;
}

// HU-10: a justificativa é opcional e a recusa não pode ser desfeita, por isso
// a confirmação nomeia a proposta em vez de perguntar de forma genérica.
@Component({
  selector: 'app-recusar-orcamento-dialog',
  standalone: true,
  imports: [FormsModule, DialogShellComponent],
  template: `
    <app-dialog-shell
      [title]="'Deseja realmente recusar o orçamento &quot;' + data.nomeDaProposta + '&quot;?'"
    >
      <label class="block">
        <span class="text-sm text-lactec-ink-soft">
          Por favor, forneça uma justificativa (opcional):
        </span>
        <textarea
          rows="4"
          class="mt-2 w-full rounded-lg border border-lactec-line bg-lactec-paper px-3 py-2
                 text-sm text-lactec-ink placeholder:text-lactec-muted
                 focus:border-lactec-primary focus:outline-none"
          placeholder="Justificativa..."
          [ngModel]="justificativa()"
          (ngModelChange)="justificativa.set($event)"
        ></textarea>
      </label>

      <div dialog-footer class="flex w-full flex-col gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          class="rounded-lg bg-lactec-danger px-5 py-3 text-sm font-semibold text-white
                 hover:opacity-90"
          (click)="rejeitar()"
        >
          Rejeitar
        </button>
        <button
          type="button"
          class="rounded-lg bg-lactec-line px-5 py-3 text-sm font-semibold text-lactec-ink
                 hover:bg-lactec-divider"
          (click)="cancelar()"
        >
          Cancelar
        </button>
      </div>
    </app-dialog-shell>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecusarOrcamentoDialogComponent {
  protected readonly data = inject<RecusarOrcamentoDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<RecusarOrcamentoDialogComponent>);

  protected readonly justificativa = signal('');

  protected rejeitar(): void {
    this.dialogRef.close({ justificativa: this.justificativa().trim() });
  }

  protected cancelar(): void {
    this.dialogRef.close();
  }
}
