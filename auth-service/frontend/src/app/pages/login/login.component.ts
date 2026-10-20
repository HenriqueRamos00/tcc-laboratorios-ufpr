import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';

import { LogoComponent } from '@shared/components/logo/logo.component';
import { AdminAuthService } from '@/app/services/admin-auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatIconModule, MatButtonModule, LogoComponent],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AdminAuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly infoMessage = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly csrfReady = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  constructor() {
    if (this.route.snapshot.queryParamMap.get('setup') === 'done') {
      this.infoMessage.set('Administrador criado com sucesso. Entre com suas credenciais.');
    }
    this.auth
      .csrf()
      .subscribe({
        next: () => this.csrfReady.set(true),
        error: () => {
          this.csrfReady.set(false);
          this.errorMessage.set(
            'Não foi possível inicializar a sessão. Recarregue a página e tente novamente.',
          );
        },
      });
    this.auth
      .setupStatus()
      .pipe(catchError(() => of(null)))
      .subscribe((status) => {
        if (status?.required) void this.router.navigateByUrl('/setup');
      });
  }

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  showRecoveryGuidance(): void {
    this.infoMessage.set(
      'A recuperação de senha ainda não está disponível. Procure o administrador do sistema.',
    );
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.csrfReady()) {
      this.errorMessage.set('Aguarde um instante e tente novamente.');
      return;
    }

    this.errorMessage.set(null);
    this.infoMessage.set(null);
    this.submitting.set(true);

    this.auth.login({ email: this.form.getRawValue().email.trim(), password: this.form.getRawValue().password }).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl('/admin/users');
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (error instanceof HttpErrorResponse && error.status === 403) {
          this.errorMessage.set('Esta conta não possui acesso administrativo.');
        } else if (
          error instanceof HttpErrorResponse &&
          (error.status === 401 || error.status === 400)
        ) {
          this.errorMessage.set('Credenciais inválidas. Confira seu e-mail e senha.');
        } else if (error instanceof HttpErrorResponse && error.status === 429) {
          this.errorMessage.set('Muitas tentativas. Aguarde alguns minutos e tente novamente.');
        } else {
          this.errorMessage.set(
            'Não foi possível entrar agora. Verifique a conexão com o serviço de autenticação.',
          );
        }
      },
    });
  }
}
