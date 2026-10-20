import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, EMPTY, finalize } from 'rxjs';

import { AdminRole, AdminUser, AdminUserRequest, CompanyRef } from '@/app/model/admin-user';
import { CompaniesService } from '@/app/services/companies.service';
import { UsersService } from '@/app/services/users.service';
import { DialogShellComponent } from '@/app/shared/components/dialog-shell/dialog-shell.component';
import { trimmedEmailValidator } from '@/app/shared/validators/trimmed-email.validator';

export interface UserFormDialogData {
  user: AdminUser | null;
}

@Component({
  selector: 'app-user-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatProgressSpinnerModule, DialogShellComponent],
  templateUrl: './user-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly users = inject(UsersService);
  private readonly companies = inject(CompaniesService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogRef = inject(MatDialogRef<UserFormDialogComponent, AdminUser>);
  readonly data = inject<UserFormDialogData>(MAT_DIALOG_DATA);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly loadingCompanies = signal(true);
  readonly companiesError = signal(false);
  readonly companyOptions = signal<CompanyRef[]>([]);
  readonly companySelectionError = signal(false);
  readonly isEditing = this.data.user !== null;

  readonly form = this.formBuilder.nonNullable.group({
    name: [
      this.data.user?.name ?? '',
      [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(150)],
    ],
    email: [
      this.data.user?.email ?? '',
      [Validators.required, trimmedEmailValidator, Validators.maxLength(254)],
    ],
    role: [
      (this.data.user?.role ?? 'USER') as AdminRole,
      [Validators.required],
    ],
    companyIds: this.formBuilder.nonNullable.control<string[]>(
      this.data.user?.companies.map((company) => company.id) ?? [],
    ),
    password: [
      '',
      this.data.user
        ? [Validators.minLength(15), Validators.maxLength(1024)]
        : [Validators.required, Validators.minLength(15), Validators.maxLength(1024)],
    ],
  });

  constructor() {
    this.form.controls.role.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.companySelectionError.set(false));
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.loadingCompanies.set(true);
    this.companiesError.set(false);
    this.companies
      .list('')
      .pipe(
        catchError(() => {
          this.companiesError.set(true);
          return EMPTY;
        }),
        finalize(() => this.loadingCompanies.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((companies) => {
        this.companyOptions.set(
          companies
            .map((company) => ({ id: company.id, name: company.name, cnpj: company.cnpj }))
            .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
        );
      });
  }

  empresaMarcada(id: string): boolean {
    return this.form.controls.companyIds.value.includes(id);
  }

  alternarEmpresa(company: CompanyRef, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const current = this.form.controls.companyIds.value;
    const ids = checked
      ? [...new Set([...current, company.id])]
      : current.filter((id) => id !== company.id);
    this.form.controls.companyIds.setValue(ids);
    this.form.controls.companyIds.markAsTouched();
    this.companySelectionError.set(false);
  }

  close(): void {
    if (!this.saving()) this.dialogRef.close();
  }

  save(): void {
    if (this.saving() || this.loadingCompanies() || this.companiesError()) return;
    this.serverError.set('');
    this.companySelectionError.set(false);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.role === 'USER' && value.companyIds.length === 0) {
      this.companySelectionError.set(true);
      return;
    }

    const request: AdminUserRequest = {
      name: value.name.trim(),
      email: value.email.trim().toLowerCase(),
      role: value.role,
      companyIds: value.companyIds,
      ...(value.password ? { password: value.password } : {}),
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    const saving$ = this.data.user
      ? this.users.update(this.data.user.id, request)
      : this.users.create(request);
    saving$
      .pipe(
        catchError((error: HttpErrorResponse) => {
          this.serverError.set(this.describeError(error));
          return EMPTY;
        }),
        finalize(() => {
          this.dialogRef.disableClose = false;
          this.saving.set(false);
        }),
      )
      .subscribe((user) => this.dialogRef.close(user));
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 409) return 'Este e-mail já está cadastrado. Informe outro e-mail.';
    if (error.status === 400) {
      return typeof error.error?.message === 'string'
        ? error.error.message
        : 'Confira os campos e as empresas selecionadas.';
    }
    if (error.status === 403) return 'Sua conta não tem permissão para manter usuários.';
    return 'Não foi possível salvar. Verifique a conexão e tente novamente.';
  }
}
