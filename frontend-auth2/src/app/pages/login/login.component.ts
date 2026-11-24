import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { LoginService } from '@/app/services/login.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login.component.html',
  host: { class: 'w-full max-w-sm' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }

    this.errorMessage.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.loginService
      .login(this.form.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: () => {
          this.form.reset();
          void this.router.navigate(['/admin'], { replaceUrl: true });
        },
        error: (error: unknown) => this.errorMessage.set(loginErrorMessage(error)),
      });
  }
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401 && !error.headers.has('WWW-Authenticate')) {
      return 'E-mail ou senha inválidos.';
    }
    if (error.status === 400) {
      return 'Confira o e-mail e a senha e tente novamente.';
    }
    if (error.status === 429 || error.status === 503) {
      const seconds = Number(error.headers.get('Retry-After'));
      const reason = error.status === 429
        ? 'Muitas tentativas de acesso.'
        : 'Serviço temporariamente indisponível.';
      return Number.isSafeInteger(seconds) && seconds > 0
        ? `${reason} Tente novamente em ${seconds} s.`
        : `${reason} Aguarde um momento e tente novamente.`;
    }
  }

  return 'Não foi possível acessar o Auth2. Tente novamente em instantes.';
}
