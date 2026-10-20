import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NgxMaskDirective, provideNgxMask } from 'ngx-mask';
import { catchError, EMPTY, finalize } from 'rxjs';

import { Company, CompanyRequest } from '@/app/model/company';
import { CompaniesService } from '@/app/services/companies.service';
import { DialogShellComponent } from '@/app/shared/components/dialog-shell/dialog-shell.component';
import { cnpjValidator, somenteDigitos } from '@/app/shared/validators/cnpj.validator';

export interface CompanyFormDialogData {
  company: Company | null;
}

@Component({
  selector: 'app-company-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, DialogShellComponent, NgxMaskDirective],
  providers: [provideNgxMask()],
  templateUrl: './company-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompanyFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly companies = inject(CompaniesService);
  private readonly dialogRef = inject(MatDialogRef<CompanyFormDialogComponent, Company>);
  readonly data = inject<CompanyFormDialogData>(MAT_DIALOG_DATA);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly isEditing = this.data.company !== null;

  readonly form = this.formBuilder.nonNullable.group({
    name: [
      this.data.company?.name ?? '',
      [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(200)],
    ],
    cnpj: [this.data.company?.cnpj ?? '', [Validators.required, cnpjValidator]],
  });

  close(): void {
    if (!this.saving()) this.dialogRef.close();
  }

  save(): void {
    if (this.saving()) return;
    this.serverError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: CompanyRequest = {
      name: value.name.trim(),
      cnpj: somenteDigitos(value.cnpj),
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    const saving$ = this.data.company
      ? this.companies.update(this.data.company.id, request)
      : this.companies.create(request);
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
      .subscribe((company) => this.dialogRef.close(company));
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 409) return 'Este CNPJ já está cadastrado. Confira o número informado.';
    if (error.status === 400) {
      return typeof error.error?.message === 'string'
        ? error.error.message
        : 'Confira os campos informados.';
    }
    if (error.status === 403) return 'Sua conta não tem permissão para manter empresas.';
    return 'Não foi possível salvar. Verifique a conexão e tente novamente.';
  }
}
