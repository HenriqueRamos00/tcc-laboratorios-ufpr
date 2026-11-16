import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { LogoComponent } from '@shared/components/logo/logo.component';
import { PasswordRecoveryService } from '@/app/services/password-recovery.service';

const passwordPolicyValidator: ValidatorFn = (control): ValidationErrors | null => {
  const password = String(control.value ?? '');
  const hasUppercase = Array.from(password).some((character) =>
    character.toUpperCase() === character && character.toLowerCase() !== character);
  const hasLowercase = Array.from(password).some((character) =>
    character.toLowerCase() === character && character.toUpperCase() !== character);
  const hasDigit = Array.from(password).some((character) => /\p{N}/u.test(character));
  return password.length >= 8 && hasUppercase && hasLowercase && hasDigit
    ? null
    : { passwordPolicy: true };
};

const matchingPasswordsValidator: ValidatorFn = (group): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirmation = group.get('confirmation')?.value;
  return !password || !confirmation || password === confirmation
    ? null
    : { passwordMismatch: true };
};

@Component({
  selector: 'app-password-recovery',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, RouterLink, LogoComponent],
  templateUrl: './password-recovery.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordRecoveryComponent {
  private readonly fb = inject(FormBuilder);
  private readonly recovery = inject(PasswordRecoveryService);
  readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  readonly submitting = signal(false);
  readonly requestSubmitted = signal(false);
  readonly resetComplete = signal(false);
  readonly passwordVisible = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(72), passwordPolicyValidator]],
    confirmation: ['', [Validators.required, Validators.maxLength(72)]],
  }, { validators: matchingPasswordsValidator });

  togglePasswordVisibility(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  submit(): void {
    this.errorMessage.set(null);
    if (this.token) {
      if (this.form.controls.password.invalid || this.form.controls.confirmation.invalid || this.form.hasError('passwordMismatch')) {
        this.form.controls.password.markAsTouched();
        this.form.controls.confirmation.markAsTouched();
        return;
      }
      this.submitting.set(true);
      this.recovery.reset(this.token, this.form.controls.password.value).subscribe({
        next: () => {
          this.submitting.set(false);
          this.resetComplete.set(true);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(error instanceof HttpErrorResponse && error.status === 400
            ? 'O link expirou, já foi utilizado ou a senha não atende aos requisitos. Solicite uma nova recuperação.'
            : 'Não foi possível redefinir a senha agora. Tente novamente em instantes.');
        },
      });
      return;
    }

    const email = this.form.controls.email;
    if (email.invalid || this.submitting()) {
      email.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.recovery.request(email.value.trim().toLowerCase()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.requestSubmitted.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Não foi possível solicitar a recuperação agora. Tente novamente em instantes.');
      },
    });
  }
}
