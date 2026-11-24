import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { filter, finalize, switchMap, tap } from 'rxjs';

import { ApplicationToken } from '@/app/model/access';
import { accessError, AccessService } from '@/app/services/access.service';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { IssueTokenDialogComponent } from './issue-token-dialog.component';

@Component({
  selector: 'app-application-tokens',
  imports: [DatePipe, MatButtonModule],
  template: `
    <section class="access-card">
      <div class="access-toolbar">
        <h2 class="display text-lg font-semibold">Tokens da aplicação</h2>
        <button mat-flat-button (click)="issue()" [disabled]="busy() || loading()">
          Emitir token
        </button>
      </div>
      @if (notice()) {
        <p class="access-notice" role="status">{{ notice() }}</p>
      }
      @if (actionError()) {
        <p class="access-error" role="alert">{{ actionError() }}</p>
      }
      @if (loading()) {
        <p class="access-empty" role="status">Carregando tokens…</p>
      } @else if (loadError()) {
        <div class="access-empty" role="alert">
          {{ loadError() }}
          <button mat-button (click)="reload()">Tentar novamente</button>
        </div>
      } @else if (!tokens().length) {
        <p class="access-empty">Nenhum token emitido para esta aplicação.</p>
      } @else {
        <div class="access-scroll">
          <table class="access-table">
            <thead>
              <tr>
                <th scope="col">Nome</th>
                <th scope="col">Emissão</th>
                <th scope="col">Vencimento</th>
                <th scope="col">Revogação</th>
                <th scope="col">Ações</th>
              </tr>
            </thead>
            <tbody>
              @for (token of tokens(); track token.id) {
                <tr>
                  <th scope="row">{{ token.name }}</th>
                  <td>{{ token.createdAt | date: 'short' }}</td>
                  <td>
                    {{ token.expiresAt ? (token.expiresAt | date: 'short') : 'Sem vencimento' }}
                  </td>
                  <td>{{ token.revokedAt ? (token.revokedAt | date: 'short') : '—' }}</td>
                  <td>
                    @if (token.revokedAt) {
                      <span class="access-badge" data-tone="neutral">Revogado</span>
                    } @else {
                      <button
                        mat-button
                        class="access-danger"
                        [disabled]="busy()"
                        (click)="revoke(token)"
                      >
                        Revogar
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TokensComponent implements OnInit {
  private readonly api = inject(AccessService);
  private readonly dialog = inject(MatDialog);
  private readonly destroy = inject(DestroyRef);
  readonly serviceId = input.required<string>();
  readonly tokens = signal<ApplicationToken[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly notice = signal('');
  readonly actionError = signal('');
  readonly busy = signal(false);

  ngOnInit() {
    this.reload();
  }

  reload() {
    this.loading.set(true);
    this.loadError.set('');
    this.api
      .tokens(this.serviceId())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (tokens) => {
          this.tokens.set(tokens);
          this.loading.set(false);
        },
        error: (e) => {
          this.loadError.set(accessError(e, 'Não foi possível carregar os tokens.'));
          this.loading.set(false);
        },
      });
  }

  issue() {
    const ref = this.dialog.open(IssueTokenDialogComponent, {
      data: this.serviceId(),
      width: '30rem',
    });
    const unregister = this.destroy.onDestroy(() => ref.close());
    ref
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        unregister();
        this.reload();
      });
  }

  revoke(token: ApplicationToken) {
    if (this.busy()) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: `Revogar ${token.name}?`,
        message: 'A aplicação perderá imediatamente o acesso com este token.',
        action: 'Revogar',
      },
    });
    ref
      .afterClosed()
      .pipe(
        filter(Boolean),
        tap(() => {
          this.busy.set(true);
          this.actionError.set('');
          this.notice.set('');
        }),
        switchMap(() => this.api.revoke(this.serviceId(), token.id)),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: () => {
          this.notice.set('Token revogado.');
          this.reload();
        },
        error: (e) => this.actionError.set(accessError(e, 'Não foi possível revogar o token.')),
      });
  }
}
