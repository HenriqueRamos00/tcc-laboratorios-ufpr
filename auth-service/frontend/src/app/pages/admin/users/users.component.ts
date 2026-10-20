import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, finalize, of, startWith, Subject, switchMap, tap } from 'rxjs';

import { AdminRole, AdminUser } from '@/app/model/admin-user';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { UsersService } from '@/app/services/users.service';
import { AdminSessionStore } from '@core/store/admin-session.store';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { UserFormDialogComponent, UserFormDialogData } from './user-form-dialog.component';

type RoleFilter = AdminRole | 'ALL';
type StatusFilter = 'ACTIVE' | 'INACTIVE' | 'ALL';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    RouterLink,
  ],
  templateUrl: './users.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersComponent {
  private readonly users = inject(UsersService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = inject(AdminSessionStore);
  private readonly refresh$ = new Subject<void>();

  readonly currentUserId = signal<string | null>(null);
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly roleControl = new FormControl<RoleFilter>('ALL', { nonNullable: true });
  readonly statusControl = new FormControl<StatusFilter>('ALL', { nonNullable: true });
  readonly usersList = signal<AdminUser[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly alterandoStatus = signal<ReadonlySet<string>>(new Set());

  constructor() {
    this.currentUserId.set(this.session.user()?.id ?? null);
    combineLatest([
      this.searchControl.valueChanges.pipe(
        startWith(this.searchControl.value),
        debounceTime(300),
        distinctUntilChanged(),
      ),
      this.roleControl.valueChanges.pipe(startWith(this.roleControl.value), distinctUntilChanged()),
      this.statusControl.valueChanges.pipe(
        startWith(this.statusControl.value),
        distinctUntilChanged(),
      ),
      this.refresh$.pipe(startWith(undefined)),
    ])
      .pipe(
        switchMap(([search, role, status]) => {
          this.carregando.set(true);
          this.erro.set(false);
          this.erroPermissao.set(false);
          const enabled = status === 'ALL' ? null : status === 'ACTIVE';
          return this.users.list(search, role === 'ALL' ? null : role, enabled).pipe(
            tap((users) => this.usersList.set(users)),
            catchError((error: HttpErrorResponse) => {
              this.usersList.set([]);
              this.erro.set(true);
              this.erroPermissao.set(error.status === 403);
              return of([] as AdminUser[]);
            }),
            finalize(() => this.carregando.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  tentarNovamente(): void {
    this.refresh$.next();
  }

  novoUsuario(): void {
    this.abrirFormulario(null);
  }

  editarUsuario(user: AdminUser): void {
    this.abrirFormulario(user);
  }

  alternarStatus(user: AdminUser): void {
    if (user.id === this.currentUserId()) return;
    const enabling = !user.enabled;
    const data: ConfirmacaoDialogData = {
      titulo: enabling ? 'Ativar usuário?' : 'Desativar usuário?',
      mensagem: enabling
        ? `${user.name} poderá acessar o portal novamente.`
        : `${user.name} perderá o acesso ao portal e ao painel administrativo.`,
      textoConfirmar: enabling ? 'Ativar' : 'Desativar',
      perigo: !enabling,
    };
    this.dialog
      .open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
        ConfirmacaoDialogComponent,
        data,
        `${enabling ? 'Confirmar ativação' : 'Confirmar desativação'} de ${user.name}`,
        { width: '420px' },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.salvarStatus(user, enabling);
      });
  }

  excluirUsuario(user: AdminUser): void {
    if (user.id === this.currentUserId()) return;
    const data: ConfirmacaoDialogData = {
      titulo: 'Excluir usuário?',
      mensagem: `A conta de ${user.name} será removida permanentemente, incluindo seus vínculos com empresas.`,
      textoConfirmar: 'Excluir',
      perigo: true,
    };
    this.dialog
      .open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
        ConfirmacaoDialogComponent,
        data,
        `Confirmar exclusão de ${user.name}`,
        { width: '440px' },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.remover(user);
      });
  }

  empresasDoUsuario(user: AdminUser): string {
    return user.companies.length
      ? user.companies.map((company) => company.name).join(', ')
      : '-';
  }

  roleLabel(role: AdminRole): string {
    return role === 'ADMIN' ? 'Administrador' : 'Usuário do portal';
  }

  private abrirFormulario(user: AdminUser | null): void {
    const data: UserFormDialogData = { user };
    this.dialog
      .openLateral<UserFormDialogComponent, UserFormDialogData, AdminUser>(
        UserFormDialogComponent,
        data,
        user ? `Editar usuário ${user.name}` : 'Cadastrar novo usuário',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((saved) => {
        if (!saved) return;
        this.snackBar.open(user ? 'Usuário atualizado.' : 'Usuário cadastrado.', 'Fechar', {
          duration: 3500,
        });
        this.tentarNovamente();
      });
  }

  private salvarStatus(user: AdminUser, enabled: boolean): void {
    this.alterandoStatus.update((current) => new Set(current).add(user.id));
    this.users
      .changeStatus(user.id, enabled)
      .pipe(
        catchError((error: HttpErrorResponse) => {
          const message =
            error.status === 409
              ? 'O sistema precisa manter pelo menos um administrador ativo.'
              : 'Não foi possível alterar o status. Tente novamente.';
          this.snackBar.open(message, 'Fechar', { duration: 5000 });
          return of(null);
        }),
        finalize(() =>
          this.alterandoStatus.update((current) => {
            const next = new Set(current);
            next.delete(user.id);
            return next;
          }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((updated) => {
        if (!updated) return;
        this.snackBar.open(enabled ? 'Usuário ativado.' : 'Usuário desativado.', 'Fechar', {
          duration: 3500,
        });
        this.tentarNovamente();
      });
  }

  private remover(user: AdminUser): void {
    this.users
      .delete(user.id)
      .pipe(
        catchError((error: HttpErrorResponse) => {
          const message =
            error.status === 409
              ? 'O sistema precisa manter pelo menos um administrador.'
              : 'Não foi possível excluir o usuário. Tente novamente.';
          this.snackBar.open(message, 'Fechar', { duration: 5000 });
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (result === null) return;
        this.snackBar.open('Usuário excluído.', 'Fechar', { duration: 3500 });
        this.tentarNovamente();
      });
  }
}
