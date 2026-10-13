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

import { Empresa, EmpresaStatus, EmpresaStatusFilter } from '@/app/model/empresa';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EmpresasService } from '@/app/services/empresas.service';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { EmpresaFormDialogComponent, EmpresaFormDialogData } from './empresa-form-dialog.component';

function formatarCnpjBr(cnpj: string): string {
  const digits = cnpj.replace(/\D/g, '');
  if (digits.length !== 14) return cnpj;
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
}

@Component({
  selector: 'app-empresas',
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
  templateUrl: './empresas.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmpresasComponent {
  private readonly companies = inject(EmpresasService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<EmpresaStatusFilter>('ALL', { nonNullable: true });
  readonly empresas = signal<Empresa[]>([]);
  readonly formatarCnpj = formatarCnpjBr;
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
        return this.companies.list(search, status === 'ALL' ? null : status).pipe(
          tap((empresas) => this.empresas.set(empresas)),
          catchError((error: HttpErrorResponse) => {
            this.empresas.set([]);
            this.erro.set(true);
            this.erroPermissao.set(error.status === 403);
            return of([] as Empresa[]);
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

  novaEmpresa(): void {
    this.abrirFormulario(null);
  }

  editarEmpresa(empresa: Empresa): void {
    this.abrirFormulario(empresa);
  }

  alternarStatus(empresa: Empresa): void {
    const novoStatus: EmpresaStatus = empresa.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const acao = novoStatus === 'INACTIVE' ? 'desativar' : 'ativar';
    const data: ConfirmacaoDialogData = {
      titulo: novoStatus === 'INACTIVE' ? 'Desativar empresa?' : 'Ativar empresa?',
      mensagem: novoStatus === 'INACTIVE'
        ? `A empresa ${empresa.legalName} deixará de conceder acesso aos seus dados. Os vínculos de clientes serão preservados.`
        : `A empresa ${empresa.legalName} ficará disponível para novos vínculos de clientes.`,
      textoConfirmar: novoStatus === 'INACTIVE' ? 'Desativar' : 'Ativar',
      perigo: novoStatus === 'INACTIVE',
    };
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent,
      data,
      `Confirmar ${acao} ${empresa.legalName}`,
      { width: '440px' },
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) this.salvarStatus(empresa, novoStatus);
    });
  }

  private abrirFormulario(empresa: Empresa | null): void {
    const data: EmpresaFormDialogData = { empresa };
    this.dialog.openLateral<EmpresaFormDialogComponent, EmpresaFormDialogData, Empresa>(
      EmpresaFormDialogComponent,
      data,
      empresa ? `Editar empresa ${empresa.legalName}` : 'Cadastrar nova empresa',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((salvo) => {
      if (!salvo) return;
      this.snackBar.open(empresa ? 'Empresa atualizada.' : 'Empresa cadastrada.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }

  private salvarStatus(empresa: Empresa, status: EmpresaStatus): void {
    this.alterandoStatus.update((atual) => new Set(atual).add(empresa.id));
    this.companies.changeStatus(empresa.id, status).pipe(
      catchError((error: HttpErrorResponse) => {
        const message = error.status === 403
          ? 'Sua conta não tem permissão para alterar empresas.'
          : 'Não foi possível alterar o status. Tente novamente.';
        this.snackBar.open(message, 'Fechar', { duration: 5000 });
        return of(null);
      }),
      finalize(() => this.alterandoStatus.update((atual) => {
        const prox = new Set(atual);
        prox.delete(empresa.id);
        return prox;
      })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((atualizada) => {
      if (!atualizada) return;
      this.snackBar.open(status === 'ACTIVE' ? 'Empresa ativada.' : 'Empresa desativada.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }
}
