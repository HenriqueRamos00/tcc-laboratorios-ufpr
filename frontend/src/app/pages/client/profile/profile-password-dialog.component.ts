import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { EMPTY, catchError, finalize } from 'rxjs';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { Profile } from '@/app/model/profile';
import { describeProfileError, isErrorCode, passwordRequirements } from '@/app/model/profile-presentation';
import { ProfileService } from '@/app/services/profile.service';

function passwordPolicyValidator(profile: Profile): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = typeof control.value === 'string' ? control.value : '';
    const failures = passwordRequirements(value, profile.passwordPolicy).filter((requirement) => !requirement.satisfied);
    return failures.length ? { passwordPolicy: failures.map((failure) => failure.key) } : null;
  };
}

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('newPassword')?.value;
  const confirmation = control.get('confirmation')?.value;
  return !confirmation || password === confirmation ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-profile-password-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DialogShellComponent, MatButtonModule, MatIconModule],
  templateUrl: './profile-password-dialog.component.html',
  styleUrl: './profile-password-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePasswordDialogComponent {
  readonly data = inject<Profile>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ProfilePasswordDialogComponent, 'changed'>);
  private readonly profiles = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly injector = inject(Injector);
  private readonly continueButton = viewChild<ElementRef<HTMLButtonElement>>('continueButton');
  private readonly newPasswordInput = viewChild<ElementRef<HTMLInputElement>>('newPasswordInput');

  protected readonly saving = signal(false);
  protected readonly discardConfirmation = signal(false);
  protected readonly serverError = signal('');
  protected readonly showCurrentPassword = signal(false);
  protected readonly showNewPassword = signal(false);
  protected readonly showConfirmation = signal(false);
  protected readonly form = this.formBuilder.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [passwordPolicyValidator(this.data)]],
    confirmation: ['', [Validators.required]],
  }, { validators: passwordsMatchValidator });
  private readonly newPasswordValue = toSignal(this.form.controls.newPassword.valueChanges, { initialValue: '' });
  private readonly confirmationValue = toSignal(this.form.controls.confirmation.valueChanges, { initialValue: '' });
  private readonly policyChecks = computed(() => passwordRequirements(this.newPasswordValue(), this.data.passwordPolicy));
  protected readonly requirements = computed(() => {
    const checks = this.policyChecks();
    return checks.filter(({ key }) => key !== 'bytes' && key !== 'maximum'
      && (key !== 'nonblank' || this.data.passwordPolicy.minCodePoints === 0));
  });
  protected readonly passwordsMatch = computed(() => {
    const confirmation = this.confirmationValue();
    return confirmation.length > 0 && confirmation === this.newPasswordValue();
  });
  protected readonly passwordTooLong = computed(() =>
    this.policyChecks().some(({ key, satisfied }) => !satisfied && (key === 'bytes' || key === 'maximum')),
  );
  protected readonly requestClose = () => this.close();

  constructor() {
    this.form.controls.currentPassword.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.serverError.set(''));
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

  protected newPasswordInvalid(): boolean {
    const control = this.form.controls.newPassword;
    return this.passwordTooLong() || (control.invalid && control.touched);
  }

  protected confirmationInvalid(): boolean {
    const control = this.form.controls.confirmation;
    return (control.invalid || this.form.hasError('passwordMismatch')) && (control.touched || control.dirty);
  }

  protected hasChanges(): boolean {
    const values = this.form.getRawValue();
    return !!values.currentPassword || !!values.newPassword || !!values.confirmation;
  }

  protected close(): void {
    if (this.saving()) return;
    if (this.discardConfirmation()) {
      this.continueEditing();
      return;
    }
    if (this.hasChanges()) {
      this.discardConfirmation.set(true);
      afterNextRender(() => this.continueButton()?.nativeElement.focus(), { injector: this.injector });
      return;
    }
    this.dialogRef.close();
  }

  protected continueEditing(): void {
    this.discardConfirmation.set(false);
    afterNextRender(() => this.newPasswordInput()?.nativeElement.focus(), { injector: this.injector });
  }

  protected discardChanges(): void {
    if (this.saving()) return;
    this.form.reset({ currentPassword: '', newPassword: '', confirmation: '' });
    this.dialogRef.close();
  }

  protected toggleCurrentPassword(): void {
    this.showCurrentPassword.update((visible) => !visible);
  }

  protected toggleNewPassword(): void {
    this.showNewPassword.update((visible) => !visible);
  }

  protected toggleConfirmation(): void {
    this.showConfirmation.update((visible) => !visible);
  }

  protected submit(): void {
    if (this.saving() || this.discardConfirmation()) return;
    this.serverError.set('');
    this.discardConfirmation.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.saving.set(true);
    this.dialogRef.disableClose = true;
    this.profiles.changePassword(currentPassword, newPassword).pipe(
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
    ).subscribe(() => this.dialogRef.close('changed'));
  }
}
