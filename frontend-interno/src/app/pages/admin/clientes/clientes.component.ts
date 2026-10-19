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
import { combineLatest, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, startWith, switchMap, tap } from 'rxjs/operators';

import { Cliente, ClienteStatus, ClienteStatusFilter } from '@/app/model/cliente';
import { formatarTelefoneBr } from '@/app/model/telefone-presentation';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { ClientesService } from '@/app/services/clientes.service';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { ClienteFormDialogComponent, ClienteFormDialogData } from './cliente-form-dialog.component';

@Component({
  selector: 'app-clientes',
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
  templateUrl: './clientes.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientesComponent {
  private readonly clients = inject(ClientesService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<ClienteStatusFilter>('ALL', { nonNullable: true });
  readonly clientes = signal<Cliente[]>([]);
  readonly formatarTelefone = formatarTelefoneBr;
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly alterandoStatus = signal<ReadonlySet<string>>(new Set());

  constructor() {
    combineLatest([
      this.searchControl.valueChanges.pipe(startWith(this.searchControl.value), debounceTime(300), distinctUntilChanged()),
      this.statusControl.valueChanges.pipe(startWith(this.statusControl.value), distinctUntilChanged()),
      this.refresh$.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([search, status]) => {
        this.carregando.set(true);
        this.erro.set(false);
        this.erroPermissao.set(false);
        return this.clients.list(search, status === 'ALL' ? null : status).pipe(
          tap((clientes) => this.clientes.set(clientes)),
          catchError((error: HttpErrorResponse) => {
            this.clientes.set([]);
            this.erro.set(true);
            this.erroPermissao.set(error.status === 403);
            return of([] as Cliente[]);
          }),
          finalize(() => this.carregando.set(false)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  tentarNovamente(): void {
    this.refresh$.next();
  }

  novoCliente(): void {
    this.abrirFormulario(null);
  }

  editarCliente(cliente: Cliente): void {
    this.abrirFormulario(cliente);
  }

  alternarStatus(cliente: Cliente): void {
    const novoStatus: ClienteStatus = cliente.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const data: ConfirmacaoDialogData = {
      titulo: novoStatus === 'INACTIVE' ? 'Desativar cliente?' : 'Ativar cliente?',
      mensagem: novoStatus === 'INACTIVE'
        ? `A conta de ${cliente.name} perderá acesso ao portal.`
        : `A conta de ${cliente.name} poderá acessar o portal novamente.`,
      textoConfirmar: novoStatus === 'INACTIVE' ? 'Desativar' : 'Ativar',
      perigo: novoStatus === 'INACTIVE',
    };
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent,
      data,
      novoStatus === 'INACTIVE' ? `Confirmar desativação de ${cliente.name}` : `Confirmar ativação de ${cliente.name}`,
      { width: '420px' },
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) this.salvarStatus(cliente, novoStatus);
    });
  }

  private abrirFormulario(cliente: Cliente | null): void {
    const data: ClienteFormDialogData = { cliente };
    this.dialog.openLateral<ClienteFormDialogComponent, ClienteFormDialogData, Cliente>(
      ClienteFormDialogComponent,
      data,
      cliente ? `Editar cliente ${cliente.name}` : 'Cadastrar novo cliente',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((salvo) => {
      if (!salvo) return;
      this.snackBar.open(cliente ? 'Cliente atualizado.' : 'Cliente cadastrado.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }

  private salvarStatus(cliente: Cliente, status: ClienteStatus): void {
    this.alterandoStatus.update((atual) => new Set(atual).add(cliente.id));
    this.clients.changeStatus(cliente.id, status).pipe(
      catchError((error: HttpErrorResponse) => {
        const message = error.status === 403
          ? 'Sua conta não tem permissão para alterar clientes.'
          : 'Não foi possível alterar o status. Tente novamente.';
        this.snackBar.open(message, 'Fechar', { duration: 5000 });
        return of(null);
      }),
      finalize(() => this.alterandoStatus.update((atual) => {
        const prox = new Set(atual);
        prox.delete(cliente.id);
        return prox;
      })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((atualizado) => {
      if (!atualizado) return;
      this.snackBar.open(status === 'ACTIVE' ? 'Cliente ativado.' : 'Cliente desativado.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }
}
