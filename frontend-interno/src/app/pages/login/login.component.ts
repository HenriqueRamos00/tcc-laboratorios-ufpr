import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

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
    MatCheckboxModule,
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
  readonly infoMessage = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    keepConnected: [true],
  });

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  showRecoveryGuidance(): void {
    this.infoMessage.set('A recuperação de senha ainda não está disponível. Procure o administrador do sistema.');
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.infoMessage.set(null);
    this.submitting.set(true);

    const { email, password, keepConnected } = this.form.getRawValue();
    this.loginService.login({ email, password }, keepConnected).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl(this.userRole.dashboardPath());
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
          this.errorMessage.set('A API não respondeu corretamente. Verifique se o backend está em execução e tente novamente.');
        } else if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
          this.errorMessage.set('Credenciais inválidas. Confira seu e-mail e senha e tente novamente.');
        } else {
          this.errorMessage.set('Não foi possível entrar agora. Tente novamente em instantes.');
        }
      },
    });
  }
}
