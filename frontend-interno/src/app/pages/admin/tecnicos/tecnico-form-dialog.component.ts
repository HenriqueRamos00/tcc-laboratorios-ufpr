import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { catchError, EMPTY, finalize } from 'rxjs';

import { CreateTecnicoRequest, Tecnico, TecnicoStatus, UpdateTecnicoRequest } from '@/app/model/tecnico';
import { TecnicosService } from '@/app/services/tecnicos.service';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

const trimmedEmailValidator: ValidatorFn = (control: AbstractControl) => {
  const value = control.value;
  if (typeof value !== 'string' || !value.trim()) return null;
  return Validators.email(new FormControl(value.trim()));
};

export interface TecnicoFormDialogData {
  tecnico: Tecnico | null;
}

@Component({
  selector: 'app-tecnico-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    DialogShellComponent,
  ],
  templateUrl: './tecnico-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TecnicoFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly technicians = inject(TecnicosService);
  private readonly dialogRef = inject(MatDialogRef<TecnicoFormDialogComponent, Tecnico>);
  readonly data = inject<TecnicoFormDialogData>(MAT_DIALOG_DATA);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly isEditing = this.data.tecnico !== null;

  readonly form = this.formBuilder.nonNullable.group({
    name: [this.data.tecnico?.name ?? '', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(150)]],
    email: [this.data.tecnico?.email ?? '', [Validators.required, trimmedEmailValidator, Validators.maxLength(254)]],
    unit: [this.data.tecnico?.unit ?? '', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(120)]],
    specialty: [this.data.tecnico?.specialty ?? '', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(160)]],
    status: [this.data.tecnico?.status ?? ('ACTIVE' as TecnicoStatus), Validators.required],
    password: ['', [Validators.maxLength(72), ...(this.data.tecnico ? [] : [Validators.required, Validators.pattern(/.*\S.*/)])]],
  });

  constructor() {
    if (this.data.tecnico?.status === 'ACTIVE') this.form.controls.status.disable();
  }

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
    const fields = {
      name: value.name.trim(),
      email: value.email.trim().toLowerCase(),
      unit: value.unit.trim(),
      specialty: value.specialty.trim(),
      status: value.status,
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    if (this.data.tecnico) {
      const request: UpdateTecnicoRequest = {
        ...fields,
        ...(value.password.trim() ? { password: value.password } : {}),
      };
      this.technicians.update(this.data.tecnico.id, request).pipe(
        catchError((error: HttpErrorResponse) => {
          this.serverError.set(this.describeError(error));
          return EMPTY;
        }),
        finalize(() => {
          this.dialogRef.disableClose = false;
          this.saving.set(false);
        }),
      ).subscribe((tecnico) => this.dialogRef.close(tecnico));
      return;
    }

    const request: CreateTecnicoRequest = { ...fields, password: value.password };
    this.technicians.create(request).pipe(
      catchError((error: HttpErrorResponse) => {
        this.serverError.set(this.describeError(error));
        return EMPTY;
      }),
      finalize(() => {
        this.dialogRef.disableClose = false;
        this.saving.set(false);
      }),
    ).subscribe((tecnico) => this.dialogRef.close(tecnico));
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 409) return 'Este e-mail já está cadastrado. Informe outro e-mail.';
    if (error.status === 400) return this.apiMessage(error) ?? 'Confira os campos informados.';
    if (error.status === 403) return 'Sua conta não tem permissão para manter técnicos.';
    return 'Não foi possível salvar. Verifique a conexão e tente novamente.';
  }

  private apiMessage(error: HttpErrorResponse): string | null {
    return typeof error.error?.message === 'string' ? error.error.message : null;
  }
}
