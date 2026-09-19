import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { FileDropzoneComponent } from '@shared/components/file-dropzone/file-dropzone.component';

@Component({
  selector: 'app-ordem-de-compra-dialog',
  standalone: true,
  imports: [DialogShellComponent, FileDropzoneComponent],
  template: `
    <app-dialog-shell title="Anexar Ordem de compra">
      <app-file-dropzone (arquivoEscolhido)="arquivo.set($event)" />

      <div dialog-footer class="flex w-full flex-col gap-3 sm:flex-row">
        <button
          type="button"
          class="w-full rounded-lg bg-lactec-line px-4 py-3 text-sm font-semibold text-lactec-ink
                 hover:bg-lactec-divider"
          (click)="voltar()"
        >
          Voltar
        </button>
        <button
          type="button"
          class="w-full rounded-lg bg-lactec-nav px-4 py-3 text-sm font-semibold
                 text-lactec-on-primary hover:bg-lactec-nav-dark disabled:opacity-40"
          [disabled]="!arquivo()"
          (click)="submeter()"
        >
          Submeter
        </button>
      </div>
    </app-dialog-shell>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdemDeCompraDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<OrdemDeCompraDialogComponent>);

  protected readonly arquivo = signal<File | null>(null);

  protected voltar(): void {
    this.dialogRef.close();
  }

  protected submeter(): void {
    const escolhido = this.arquivo();
    if (escolhido) this.dialogRef.close(escolhido);
  }
}
