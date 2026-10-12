import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NgxMaskDirective, provideNgxMask } from 'ngx-mask';
import { catchError, EMPTY, finalize } from 'rxjs';

import { Empresa, EmpresaRequest, EmpresaStatus } from '@/app/model/empresa';
import { EmpresasService } from '@/app/services/empresas.service';
import { trimmedEmailValidator } from '@shared/validators/trimmed-email.validator';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { cnpjValidator, ESTADOS_BRASILEIROS, trimmedRequired, ufBrasileiraValidator } from './empresa-validation';

export interface EmpresaFormDialogData {
  empresa: Empresa | null;
}

@Component({
  selector: 'app-empresa-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, DialogShellComponent, NgxMaskDirective],
  providers: [provideNgxMask()],
  templateUrl: './empresa-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmpresaFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly companies = inject(EmpresasService);
  private readonly dialogRef = inject(MatDialogRef<EmpresaFormDialogComponent, Empresa>);
  readonly data = inject<EmpresaFormDialogData>(MAT_DIALOG_DATA);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly isEditing = this.data.empresa !== null;
  readonly estados = ESTADOS_BRASILEIROS;

  readonly form = this.formBuilder.nonNullable.group({
    legalName: [this.data.empresa?.legalName ?? '', [...trimmedRequired, Validators.maxLength(200)]],
    cnpj: [this.data.empresa?.cnpj ?? '', [Validators.required, cnpjValidator]],
    address: [this.data.empresa?.address ?? '', [...trimmedRequired, Validators.maxLength(255)]],
    city: [this.data.empresa?.city ?? '', [...trimmedRequired, Validators.maxLength(120)]],
    state: [this.data.empresa?.state ?? '', [Validators.required, Validators.maxLength(2), ufBrasileiraValidator]],
    contactName: [this.data.empresa?.contactName ?? '', [...trimmedRequired, Validators.maxLength(150)]],
    contactPhone: [this.data.empresa?.contactPhone ?? '', [...trimmedRequired, Validators.maxLength(30)]],
    contactEmail: [this.data.empresa?.contactEmail ?? '', [Validators.required, trimmedEmailValidator, Validators.maxLength(254)]],
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
    const request: EmpresaRequest = {
      legalName: value.legalName.trim(),
      cnpj: value.cnpj.replace(/\D/g, ''),
      address: value.address.trim(),
      city: value.city.trim(),
      state: value.state.trim().toUpperCase(),
      contactName: value.contactName.trim(),
      contactPhone: value.contactPhone.trim(),
      contactEmail: value.contactEmail.trim().toLowerCase(),
      status: this.data.empresa?.status ?? ('ACTIVE' as EmpresaStatus),
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    const saving$ = this.data.empresa
      ? this.companies.update(this.data.empresa.id, request)
      : this.companies.create(request);
    saving$.pipe(
      catchError((error: HttpErrorResponse) => {
        this.serverError.set(this.describeError(error));
        return EMPTY;
      }),
      finalize(() => {
        this.dialogRef.disableClose = false;
        this.saving.set(false);
      }),
    ).subscribe((empresa) => this.dialogRef.close(empresa));
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 409) return 'Este CNPJ já está cadastrado. Confira o número informado.';
    if (error.status === 400) return this.apiMessage(error) ?? 'Confira os campos informados.';
    if (error.status === 403) return 'Sua conta não tem permissão para manter empresas.';
    return 'Não foi possível salvar. Verifique a conexão e tente novamente.';
  }

  private apiMessage(error: HttpErrorResponse): string | null {
    return typeof error.error?.message === 'string' ? error.error.message : null;
  }
}
