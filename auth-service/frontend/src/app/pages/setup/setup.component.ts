import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';

import { LogoComponent } from '@shared/components/logo/logo.component';
import { AdminAuthService } from '@/app/services/admin-auth.service';

@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, LogoComponent],
  templateUrl: './setup.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SetupComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AdminAuthService);
  readonly router = inject(Router);

  readonly showPassword = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly setupUnavailable = signal(false);
  readonly notRequired = signal(false);

  readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(15), Validators.maxLength(1024)]],
    confirmPassword: ['', [Validators.required]],
  });

  constructor() {
    this.auth
      .setupStatus()
      .pipe(catchError(() => of(null)))
      .subscribe((status) => {
        if (!status) return;
        if (!status.required) {
          this.notRequired.set(true);
          void this.router.navigateByUrl('/login');
        } else if (!status.enabled) {
          this.setupUnavailable.set(true);
        }
      });
  }

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  submit(): void {
    if (this.submitting() || this.setupUnavailable() || this.notRequired()) return;
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.password !== value.confirmPassword) {
      this.errorMessage.set('A confirmação de senha não confere.');
      return;
    }

    this.submitting.set(true);
    this.auth
      .setup(
        {
          name: value.name.trim(),
          email: value.email.trim().toLowerCase(),
          password: value.password,
        },
        value.token,
      )
      .subscribe({
        next: () => {
          this.submitting.set(false);
          void this.router.navigate(['/login'], { queryParams: { setup: 'done' } });
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (error instanceof HttpErrorResponse && error.status === 403) {
            this.errorMessage.set('Token de configuração inválido. Confira o valor informado.');
          } else if (error instanceof HttpErrorResponse && error.status === 409) {
            this.errorMessage.set('A configuração inicial já foi concluída. Faça login.');
          } else if (error instanceof HttpErrorResponse && error.status === 429) {
            this.errorMessage.set('Muitas tentativas. Aguarde alguns minutos e tente novamente.');
          } else if (error instanceof HttpErrorResponse && error.status === 400) {
            this.errorMessage.set(
              typeof error.error?.message === 'string'
                ? error.error.message
                : 'Confira os dados informados.',
            );
          } else {
            this.errorMessage.set(
              'Não foi possível concluir a configuração. Verifique a conexão com o serviço.',
            );
          }
        },
      });
  }
}
