import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

export interface ResumeDraftDialogData {
  readonly proposalCode: string;
  readonly savedAt: Date;
}

export type ResumeDraftChoice = 'resume' | 'start-over';

@Component({
  selector: 'app-resume-draft-dialog',
  standalone: true,
  imports: [DatePipe, DialogShellComponent],
  template: `
    <app-dialog-shell
      title="Continuar de onde parou?"
      subtitle="Encontramos neste navegador um termo de aceite deste orçamento que não foi enviado."
    >
      <p class="m-0 text-sm text-lactec-ink">
        O rascunho da proposta {{ data.proposalCode }} foi guardado em
        {{ data.savedAt | date: "dd/MM/yyyy 'às' HH:mm" }}. Você pode continuar o preenchimento ou
        apagar o rascunho e começar um termo em branco.
      </p>

      <div dialog-footer class="flex w-full flex-col gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          class="rounded-lg bg-lactec-line px-5 py-3 text-sm font-semibold text-lactec-ink
                 hover:bg-lactec-divider"
          (click)="choose('start-over')"
        >
          Começar em branco
        </button>
        <button
          type="button"
          class="rounded-lg bg-lactec-primary-dark px-5 py-3 text-sm font-semibold
                 text-lactec-on-primary hover:opacity-90"
          (click)="choose('resume')"
        >
          Continuar preenchimento
        </button>
      </div>
    </app-dialog-shell>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumeDraftDialogComponent {
  protected readonly data = inject<ResumeDraftDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(
    MatDialogRef<ResumeDraftDialogComponent, ResumeDraftChoice>,
  );

  protected choose(choice: ResumeDraftChoice): void {
    this.dialogRef.close(choice);
  }
}
