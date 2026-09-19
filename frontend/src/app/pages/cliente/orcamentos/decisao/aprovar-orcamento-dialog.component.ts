import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

import type { FormaDeAprovacao } from '@/app/model/acceptance';

// Bifurcação do aceite (HU-09): o cliente pode anexar a ordem de compra que já
// possui ou preencher o Termo de Aceite (Anexo II) pelo portal.
@Component({
  selector: 'app-aprovar-orcamento-dialog',
  standalone: true,
  imports: [DialogShellComponent],
  template: `
    <app-dialog-shell title="Como deseja aprovar o orçamento?">
      <div class="flex flex-col gap-3">
        <button
          type="button"
          class="w-full rounded-lg bg-lactec-nav px-4 py-3 text-sm font-semibold
                 text-lactec-on-primary hover:bg-lactec-nav-dark"
          (click)="escolher('ordem-de-compra')"
        >
          Tenho Ordem de Compra
        </button>
        <button
          type="button"
          class="w-full rounded-lg bg-lactec-nav px-4 py-3 text-sm font-semibold
                 text-lactec-on-primary hover:bg-lactec-nav-dark"
          (click)="escolher('termo-de-aceite')"
        >
          Preencher Termo de Aceite
        </button>
      </div>
    </app-dialog-shell>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AprovarOrcamentoDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<AprovarOrcamentoDialogComponent>);

  protected escolher(forma: FormaDeAprovacao): void {
    this.dialogRef.close(forma);
  }
}
