import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';

import { ApiKey, ApiKeyCreateResponse } from '@/app/model/api-key';
import { ApiKeysService } from '@/app/services/api-keys.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { ApiKeyFormDialogComponent } from './api-key-form-dialog.component';

@Component({
  selector: 'app-api-keys',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    RouterLink,
  ],
  templateUrl: './api-keys.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiKeysComponent {
  private readonly apiKeys = inject(ApiKeysService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly keys = signal<ApiKey[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly alterandoStatus = signal<ReadonlySet<string>>(new Set());

  constructor() {
    this.load();
  }

  load(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.erroPermissao.set(false);
    this.apiKeys
      .list()
      .pipe(
        catchError((error: HttpErrorResponse) => {
          this.keys.set([]);
          this.erro.set(true);
          this.erroPermissao.set(error.status === 403);
          return of([] as ApiKey[]);
        }),
        finalize(() => this.carregando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((keys) => this.keys.set(keys));
  }

  novaChave(): void {
    this.dialog
      .openLateral<ApiKeyFormDialogComponent, unknown, ApiKeyCreateResponse>(
        ApiKeyFormDialogComponent,
        undefined,
        'Cadastrar nova chave de integração',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((created) => {
        if (!created) return;
        this.snackBar.open('Chave de integração criada.', 'Fechar', { duration: 3500 });
        this.load();
      });
  }

  alternarStatus(key: ApiKey): void {
    const enabling = !key.enabled;
    const data: ConfirmacaoDialogData = {
      titulo: enabling ? 'Reativar chave?' : 'Revogar chave?',
      mensagem: enabling
        ? `A chave ${key.name} voltará a autorizar chamadas ao serviço.`
        : `A chave ${key.name} deixará de autorizar chamadas imediatamente.`,
      textoConfirmar: enabling ? 'Reativar' : 'Revogar',
      perigo: !enabling,
    };
    this.dialog
      .open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
        ConfirmacaoDialogComponent,
        data,
        `${enabling ? 'Confirmar reativação' : 'Confirmar revogação'} de ${key.name}`,
        { width: '440px' },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.salvarStatus(key, enabling);
      });
  }

  excluirChave(key: ApiKey): void {
    const data: ConfirmacaoDialogData = {
      titulo: 'Excluir chave?',
      mensagem: `A chave ${key.name} será removida permanentemente. Serviços que a utilizam perderão o acesso.`,
      textoConfirmar: 'Excluir',
      perigo: true,
    };
    this.dialog
      .open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
        ConfirmacaoDialogComponent,
        data,
        `Confirmar exclusão de ${key.name}`,
        { width: '440px' },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.remover(key);
      });
  }

  expirada(key: ApiKey): boolean {
    return !!key.expiresAt && new Date(key.expiresAt).getTime() <= Date.now();
  }

  statusLabel(key: ApiKey): string {
    if (this.expirada(key)) return 'Expirada';
    return key.enabled ? 'Ativa' : 'Revogada';
  }

  private salvarStatus(key: ApiKey, enabled: boolean): void {
    this.alterandoStatus.update((current) => new Set(current).add(key.id));
    this.apiKeys
      .changeStatus(key.id, enabled)
      .pipe(
        catchError(() => {
          this.snackBar.open('Não foi possível alterar o status da chave.', 'Fechar', {
            duration: 5000,
          });
          return of(null);
        }),
        finalize(() =>
          this.alterandoStatus.update((current) => {
            const next = new Set(current);
            next.delete(key.id);
            return next;
          }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((updated) => {
        if (!updated) return;
        this.snackBar.open(enabled ? 'Chave reativada.' : 'Chave revogada.', 'Fechar', {
          duration: 3500,
        });
        this.load();
      });
  }

  private remover(key: ApiKey): void {
    this.apiKeys
      .delete(key.id)
      .pipe(
        catchError(() => {
          this.snackBar.open('Não foi possível excluir a chave.', 'Fechar', { duration: 5000 });
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (result === null) return;
        this.snackBar.open('Chave excluída.', 'Fechar', { duration: 3500 });
        this.load();
      });
  }
}
