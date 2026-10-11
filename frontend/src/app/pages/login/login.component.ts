import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
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
    MatCheckboxModule,
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

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
    keepConnected: [true],
  });

  togglePassword(): void {
    this.showPassword.update((visivel) => !visivel);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);

    const { email, password, keepConnected } = this.form.getRawValue();
    this.loginService.login({ email: email.trim(), password }, keepConnected).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.userRole.dashboardPath());
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error.status === 401 || error.status === 400
            ? 'Credenciais inválidas.'
            : 'Não foi possível acessar o serviço. Tente novamente mais tarde.',
        );
      },
    });
  }
}
