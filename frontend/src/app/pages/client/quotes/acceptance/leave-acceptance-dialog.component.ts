import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

export interface LeaveAcceptanceDialogData {
  readonly draftSaved: boolean;
  readonly validityDays: number;
}

export type LeaveAcceptanceChoice = 'keep-draft' | 'discard';

@Component({
  selector: 'app-leave-acceptance-dialog',
  standalone: true,
  imports: [DialogShellComponent],
  template: `
    <app-dialog-shell
      title="Sair do termo de aceite?"
      subtitle="O termo ainda não foi enviado ao Lactec."
    >
      @if (data.draftSaved) {
        <p class="m-0 text-sm text-lactec-ink">
          Guarde o rascunho para continuar de onde parou nos próximos {{ data.validityDays }} dias,
          neste navegador e com o seu usuário, ou descarte o que foi preenchido.
        </p>
      } @else {
        <p class="m-0 text-sm text-lactec-ink">
          Este navegador não permitiu guardar o rascunho. Se você sair agora, o que foi preenchido
          será perdido.
        </p>
      }

      <div dialog-footer class="flex w-full flex-col gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          class="rounded-lg bg-lactec-line px-5 py-3 text-sm font-semibold text-lactec-ink
                 hover:bg-lactec-divider"
          (click)="stay()"
        >
          Continuar editando
        </button>
        <button
          type="button"
          class="rounded-lg border border-lactec-danger-ink bg-lactec-paper px-5 py-3 text-sm
                 font-semibold text-lactec-danger-ink hover:bg-lactec-danger-surface"
          (click)="leave('discard')"
        >
          {{ data.draftSaved ? 'Descartar e sair' : 'Sair sem guardar' }}
        </button>
        @if (data.draftSaved) {
          <button
            type="button"
            class="rounded-lg bg-lactec-primary-strong px-5 py-3 text-sm font-semibold
                   text-lactec-on-primary hover:opacity-90"
            (click)="leave('keep-draft')"
          >
            Guardar rascunho e sair
          </button>
        }
      </div>
    </app-dialog-shell>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LeaveAcceptanceDialogComponent {
  protected readonly data = inject<LeaveAcceptanceDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(
    MatDialogRef<LeaveAcceptanceDialogComponent, LeaveAcceptanceChoice>,
  );

  protected leave(choice: LeaveAcceptanceChoice): void {
    this.dialogRef.close(choice);
  }

  protected stay(): void {
    this.dialogRef.close();
  }
}
