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
import { combineLatest, Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, startWith, switchMap, tap } from 'rxjs/operators';

import { Tecnico, TecnicoFilterStatus, TecnicoStatus } from '@/app/model/tecnico';
import { TecnicosService } from '@/app/services/tecnicos.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { TecnicoFormDialogComponent, TecnicoFormDialogData } from './tecnico-form-dialog.component';

@Component({
  selector: 'app-tecnicos',
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
  templateUrl: './tecnicos.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TecnicosComponent {
  private readonly technicians = inject(TecnicosService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<TecnicoFilterStatus>('ALL', { nonNullable: true });
  readonly tecnicos = signal<Tecnico[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly alterandoStatus = signal<ReadonlySet<number>>(new Set());

  constructor() {
    combineLatest([
      this.searchControl.valueChanges.pipe(
        startWith(this.searchControl.value),
        debounceTime(300),
        distinctUntilChanged(),
      ),
      this.statusControl.valueChanges.pipe(startWith(this.statusControl.value), distinctUntilChanged()),
      this.refresh$.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([search, status]) => {
        this.carregando.set(true);
        this.erro.set(false);
        this.erroPermissao.set(false);
        const apiStatus = status === 'ALL' ? null : status;
        return this.technicians.list(search, apiStatus).pipe(
          tap((tecnicos) => this.tecnicos.set(tecnicos)),
          catchError((error: HttpErrorResponse) => {
            this.tecnicos.set([]);
            this.erro.set(true);
            this.erroPermissao.set(error.status === 403);
            return of([] as Tecnico[]);
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

  novoTecnico(): void {
    this.abrirFormulario(null);
  }

  editarTecnico(tecnico: Tecnico): void {
    this.abrirFormulario(tecnico);
  }

  alternarStatus(tecnico: Tecnico): void {
    const acao = tecnico.status === 'ACTIVE' ? 'desativar' : 'ativar';
    const data: ConfirmacaoDialogData = {
      titulo: acao === 'desativar' ? 'Desativar técnico?' : 'Ativar técnico?',
      mensagem: acao === 'desativar'
        ? `A conta de ${tecnico.name} perderá acesso ao Portal Interno.`
        : `A conta de ${tecnico.name} poderá acessar o Portal Interno novamente.`,
      textoConfirmar: acao === 'desativar' ? 'Desativar' : 'Ativar',
      perigo: acao === 'desativar',
    };
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent,
      data,
      acao === 'desativar' ? `Confirmar desativação de ${tecnico.name}` : `Confirmar ativação de ${tecnico.name}`,
      { width: '420px' },
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) this.salvarStatus(tecnico, acao === 'ativar' ? 'ACTIVE' : 'INACTIVE');
    });
  }

  private abrirFormulario(tecnico: Tecnico | null): void {
    const data: TecnicoFormDialogData = { tecnico };
    this.dialog.openLateral<TecnicoFormDialogComponent, TecnicoFormDialogData, Tecnico>(
      TecnicoFormDialogComponent,
      data,
      tecnico ? `Editar técnico ${tecnico.name}` : 'Cadastrar novo técnico',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((salvo) => {
      if (!salvo) return;
      this.snackBar.open(tecnico ? 'Técnico atualizado.' : 'Técnico cadastrado.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }

  private salvarStatus(tecnico: Tecnico, status: TecnicoStatus): void {
    this.alterandoStatus.update((atual) => new Set(atual).add(tecnico.id));
    this.technicians.changeStatus(tecnico.id, status).pipe(
      catchError((error: HttpErrorResponse) => {
        const message = error.status === 403
          ? 'Sua conta não tem permissão para alterar técnicos.'
          : 'Não foi possível alterar o status. Tente novamente.';
        this.snackBar.open(message, 'Fechar', { duration: 5000 });
        return of(null);
      }),
      finalize(() => this.alterandoStatus.update((atual) => {
        const prox = new Set(atual);
        prox.delete(tecnico.id);
        return prox;
      })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((atualizado) => {
      if (!atualizado) return;
      this.snackBar.open(status === 'ACTIVE' ? 'Técnico ativado.' : 'Técnico desativado.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }
}
