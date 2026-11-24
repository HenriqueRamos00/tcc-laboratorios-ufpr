import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { AccessService, accessError } from '@/app/services/access.service';

@Component({
  imports: [FormsModule, MatButtonModule, MatDialogModule],
  template: `
    @if (secret(); as value) {
      <h2 mat-dialog-title>Token emitido</h2>
      <mat-dialog-content class="access-fields">
        <label
          >Valor do token
          <input
            #plain
            class="access-input mono"
            readonly
            [value]="value"
            (focus)="plain.select()"
          />
          <small>Copie e guarde este valor agora. Ele só aparece nesta janela.</small>
        </label>
        @if (copyMessage()) {
          <p role="status">{{ copyMessage() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button type="button" (click)="copy(plain)">Copiar token</button>
        <button mat-flat-button type="button" mat-dialog-close>Concluir</button>
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>Emitir token</h2>
      <form ngNativeValidate (ngSubmit)="issue()">
        <mat-dialog-content>
          <fieldset class="access-fields" [disabled]="busy()">
            <label
              >Nome do token
              <input class="access-input" name="name" [(ngModel)]="name" maxlength="150" required />
            </label>
            <label
              >Válido até (opcional)
              <input
                class="access-input"
                type="datetime-local"
                name="expiry"
                [(ngModel)]="expiry"
              />
              <small>Deixe em branco para emitir sem vencimento.</small>
            </label>
          </fieldset>
          @if (error()) {
            <p class="access-error" role="alert">{{ error() }}</p>
          }
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-stroked-button type="button" mat-dialog-close [disabled]="busy()">
            Cancelar
          </button>
          <button mat-flat-button type="submit" [disabled]="busy()">
            {{ busy() ? 'Emitindo…' : 'Emitir token' }}
          </button>
        </mat-dialog-actions>
      </form>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IssueTokenDialogComponent {
  private readonly api = inject(AccessService);
  private readonly serviceId = inject<string>(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialogRef<IssueTokenDialogComponent>);
  private readonly destroy = inject(DestroyRef);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly secret = signal('');
  readonly copyMessage = signal('');
  name = '';
  expiry = '';

  issue() {
    if (this.busy() || this.secret()) return;
    this.error.set('');
    if (!this.name.trim()) {
      this.error.set('Informe o nome do token.');
      return;
    }
    const seconds = this.expiry ? Math.ceil((Date.parse(this.expiry) - Date.now()) / 1000) : null;
    if (seconds !== null && (!Number.isSafeInteger(seconds) || seconds <= 0)) {
      this.error.set('Escolha uma data de vencimento futura.');
      return;
    }
    this.busy.set(true);
    // Depois de emitido, o valor não pode ser consultado de novo: só 'Concluir' fecha a janela.
    this.dialog.disableClose = true;
    this.api
      .issue(this.serviceId, this.name.trim(), seconds)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (issued) => {
          this.busy.set(false);
          this.secret.set(issued.accessToken);
        },
        error: (e) => {
          this.busy.set(false);
          this.dialog.disableClose = false;
          this.error.set(accessError(e, 'Não foi possível emitir o token.'));
        },
      });
  }

  async copy(input: HTMLInputElement) {
    try {
      await navigator.clipboard.writeText(this.secret());
      this.copyMessage.set('Token copiado.');
    } catch {
      input.focus();
      input.select();
      this.copyMessage.set('Valor selecionado. Use o comando de cópia do dispositivo.');
    }
  }
}
