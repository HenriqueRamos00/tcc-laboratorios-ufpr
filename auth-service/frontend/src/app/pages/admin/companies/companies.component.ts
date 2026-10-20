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

import { Company } from '@/app/model/company';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { CompaniesService } from '@/app/services/companies.service';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { formatarCnpj } from '@/app/shared/validators/cnpj.validator';
import { CompanyFormDialogComponent, CompanyFormDialogData } from './company-form-dialog.component';

@Component({
  selector: 'app-companies',
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
  templateUrl: './companies.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompaniesComponent {
  private readonly companies = inject(CompaniesService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly companiesList = signal<Company[]>([]);
  readonly formatarCnpj = formatarCnpj;
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly removendo = signal<ReadonlySet<string>>(new Set());

  constructor() {
    combineLatest([
      this.searchControl.valueChanges.pipe(
        startWith(this.searchControl.value),
        debounceTime(300),
        distinctUntilChanged(),
      ),
      this.refresh$.pipe(startWith(undefined)),
    ])
      .pipe(
        switchMap(([search]) => {
          this.carregando.set(true);
          this.erro.set(false);
          this.erroPermissao.set(false);
          return this.companies.list(search).pipe(
            tap((companies) => this.companiesList.set(companies)),
            catchError((error: HttpErrorResponse) => {
              this.companiesList.set([]);
              this.erro.set(true);
              this.erroPermissao.set(error.status === 403);
              return of([] as Company[]);
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

  novaEmpresa(): void {
    this.abrirFormulario(null);
  }

  editarEmpresa(company: Company): void {
    this.abrirFormulario(company);
  }

  excluirEmpresa(company: Company): void {
    const data: ConfirmacaoDialogData = {
      titulo: 'Excluir empresa?',
      mensagem: `A empresa ${company.name} será removida permanentemente. Empresas com usuários vinculados não podem ser excluídas.`,
      textoConfirmar: 'Excluir',
      perigo: true,
    };
    this.dialog
      .open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
        ConfirmacaoDialogComponent,
        data,
        `Confirmar exclusão de ${company.name}`,
        { width: '440px' },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.remover(company);
      });
  }

  private abrirFormulario(company: Company | null): void {
    const data: CompanyFormDialogData = { company };
    this.dialog
      .openLateral<CompanyFormDialogComponent, CompanyFormDialogData, Company>(
        CompanyFormDialogComponent,
        data,
        company ? `Editar empresa ${company.name}` : 'Cadastrar nova empresa',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((saved) => {
        if (!saved) return;
        this.snackBar.open(company ? 'Empresa atualizada.' : 'Empresa cadastrada.', 'Fechar', {
          duration: 3500,
        });
        this.tentarNovamente();
      });
  }

  private remover(company: Company): void {
    this.removendo.update((current) => new Set(current).add(company.id));
    this.companies
      .delete(company.id)
      .pipe(
        catchError((error: HttpErrorResponse) => {
          const message =
            error.status === 409
              ? 'Remova os vínculos dos usuários antes de excluir esta empresa.'
              : 'Não foi possível excluir a empresa. Tente novamente.';
          this.snackBar.open(message, 'Fechar', { duration: 5000 });
          return of(null);
        }),
        finalize(() =>
          this.removendo.update((current) => {
            const next = new Set(current);
            next.delete(company.id);
            return next;
          }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (result === null) return;
        this.snackBar.open('Empresa excluída.', 'Fechar', { duration: 3500 });
        this.tentarNovamente();
      });
  }
}
