import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { EMPTY, catchError, finalize } from 'rxjs';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { trimmedEmailValidator } from '@shared/validators/trimmed-email.validator';
import { PendingEmailChange } from '@/app/model/profile';
import { describeProfileError, isErrorCode } from '@/app/model/profile-presentation';
import { ProfileService } from '@/app/services/profile.service';

export interface ProfileEmailDialogData {
  readonly currentEmail: string;
}

function differentEmailValidator(currentEmail: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const email = typeof control.value === 'string' ? control.value.trim() : '';
    return !email || email.toLocaleLowerCase('pt-BR') !== currentEmail.trim().toLocaleLowerCase('pt-BR')
      ? null
      : { sameEmail: true };
  };
}

@Component({
  selector: 'app-profile-email-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DialogShellComponent, MatButtonModule, MatIconModule],
  templateUrl: './profile-email-dialog.component.html',
  styleUrl: './profile-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileEmailDialogComponent {
  readonly data = inject<ProfileEmailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ProfileEmailDialogComponent, PendingEmailChange>);
  private readonly profiles = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly discardConfirmation = signal(false);
  protected readonly showCurrentPassword = signal(false);
  protected readonly serverError = signal('');
  protected readonly form = this.formBuilder.nonNullable.group({
    newEmail: ['', [Validators.required, trimmedEmailValidator, Validators.maxLength(254), differentEmailValidator(this.data.currentEmail)]],
    currentPassword: ['', [Validators.required]],
  });
  protected readonly requestClose = () => this.close();

  constructor() {
    this.form.controls.currentPassword.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.serverError.set(''));
  }

  protected emailError(): string {
    const email = this.form.controls.newEmail;
    if (email.hasError('required')) return 'Informe o novo endereço de e-mail.';
    if (email.hasError('email')) return 'Informe um endereço de e-mail válido.';
    if (email.hasError('sameEmail')) return 'O novo e-mail deve ser diferente do endereço atual.';
    if (email.hasError('maxlength')) return 'O e-mail deve ter até 254 caracteres.';
    return 'Confira o endereço de e-mail informado.';
  }

  protected currentPasswordInvalid(): boolean {
    const control = this.form.controls.currentPassword;
    return control.invalid && (control.touched || control.dirty);
  }

  protected currentPasswordError(): string {
    return this.form.controls.currentPassword.hasError('incorrect')
      ? 'A senha atual está incorreta.'
      : 'Informe a senha atual.';
  }

  protected hasChanges(): boolean {
    const values = this.form.getRawValue();
    return !!values.newEmail.trim() || !!values.currentPassword;
  }

  protected toggleCurrentPassword(): void {
    this.showCurrentPassword.update((visible) => !visible);
  }

  protected close(): void {
    if (this.saving()) return;
    if (this.hasChanges()) {
      this.discardConfirmation.set(true);
      return;
    }
    this.dialogRef.close();
  }

  protected continueEditing(): void {
    this.discardConfirmation.set(false);
  }

  protected discardChanges(): void {
    if (this.saving()) return;
    this.form.controls.currentPassword.setValue('');
    this.dialogRef.close();
  }

  protected submit(): void {
    if (this.saving()) return;
    this.serverError.set('');
    this.discardConfirmation.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { newEmail, currentPassword } = this.form.getRawValue();
    this.saving.set(true);
    this.dialogRef.disableClose = true;
    this.profiles.requestEmailChange(newEmail.trim(), currentPassword).pipe(
      catchError((error: unknown) => {
        if (isErrorCode(error, 'current-password-incorrect')) {
          this.form.controls.currentPassword.setErrors({ incorrect: true });
          this.form.controls.currentPassword.markAsTouched();
        } else {
          this.serverError.set(describeProfileError(error));
        }
        return EMPTY;
      }),
      finalize(() => {
        this.saving.set(false);
        this.dialogRef.disableClose = false;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((pending) => {
      this.form.controls.currentPassword.setValue('');
      this.dialogRef.close(pending);
    });
  }
}
