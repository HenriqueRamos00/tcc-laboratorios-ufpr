import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer, takeWhile } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';

import { UserRole } from '@core/store/user-role.store';
import { LogoComponent } from '@shared/components/logo/logo.component';

import { LoginService } from '@/app/services/login.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatIconModule,
    MatButtonModule,
    RouterLink,
    LogoComponent,
  ],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);
  private readonly userRole = inject(UserRole);

  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cooldownUntil = signal(0);
  private readonly now = signal(Date.now());
  readonly cooldownRemaining = computed(() => Math.max(0, Math.ceil((this.cooldownUntil() - this.now()) / 1000)));

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });

  togglePassword(): void {
    this.showPassword.update((visivel) => !visivel);
  }

  submit(): void {
    if (this.form.invalid || this.submitting() || this.cooldownRemaining() > 0) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);

    const { email, password } = this.form.getRawValue();
    this.loginService.login({ email, password }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.userRole.dashboardPath());
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        if (error.status === 429) {
          const seconds = Number(error.headers.get('Retry-After'));
          if (Number.isFinite(seconds) && seconds > 0) {
            this.now.set(Date.now());
            this.cooldownUntil.set(Date.now() + seconds * 1000);
            timer(0, 1000).pipe(
              takeUntilDestroyed(this.destroyRef),
              takeWhile(() => this.cooldownRemaining() > 0),
            ).subscribe(() => this.now.set(Date.now()));
          }
          this.errorMessage.set('Limite de tentativas atingido. Aguarde antes de tentar novamente.');
        } else if (error.status === 401) {
          this.errorMessage.set('Credenciais inválidas. Confira seu e-mail e senha.');
        } else if (error.status === 400) {
          this.errorMessage.set('Confira os dados informados e tente novamente.');
        } else {
          this.errorMessage.set('Não foi possível entrar no momento. Tente novamente em instantes.');
        }
      },
    });
  }
}
