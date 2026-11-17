import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { LogoComponent } from '@/app/shared/components/logo/logo.component';
import { PasswordRecoveryService } from '@/app/services/password-recovery.service';

@Component({
  selector: 'app-email-confirmation',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, RouterLink, LogoComponent],
  template: `
    <div class="min-h-svh grid grid-rows-[auto_1fr] md:grid-rows-none md:grid-cols-2 bg-lactec-paper">
      <section class="bg-lactec-nav bg-login-glow text-white grid place-items-center px-6 py-10 md:p-10">
        <div class="text-center max-w-sm flex flex-col items-center gap-4 md:gap-6">
          <app-logo [size]="36" />
          <h1 class="display text-2xl md:text-3xl font-semibold tracking-tight leading-tight m-0">Portal do Cliente Lactec</h1>
          <p class="hidden md:block text-sm leading-relaxed text-white/70 max-w-xs m-0">Instituto de Tecnologia para o Desenvolvimento</p>
        </div>
      </section>
      <section class="grid grid-rows-[1fr_auto] gap-6 p-6 md:p-10 bg-lactec-paper">
        <main class="self-center justify-self-center w-full max-w-md rounded-3xl border border-lactec-border bg-white p-6 md:p-9 shadow-xl">
          @if (state() === 'success') {
            <div class="flex flex-col gap-5" role="status">
              <mat-icon class="text-lactec-primary-dark" aria-hidden="true">mark_email_read</mat-icon>
              <div>
                <p class="m-0 text-xs font-semibold uppercase tracking-[0.16em] text-lactec-primary-dark">Cadastro confirmado</p>
                <h2 class="display text-2xl font-semibold text-lactec-nav tracking-tight m-0 mt-2">E-mail confirmado</h2>
                <p class="text-sm leading-relaxed text-lactec-ink-soft mt-3 mb-0">Agora você pode acessar sua conta no Portal do Cliente.</p>
              </div>
              <a mat-flat-button color="primary" routerLink="/login" class="w-full h-11">Ir para o login</a>
            </div>
          } @else if (state() === 'error') {
            <div class="flex flex-col gap-5" role="alert">
              <mat-icon class="text-lactec-danger-ink" aria-hidden="true">error_outline</mat-icon>
              <div>
                <p class="m-0 text-xs font-semibold uppercase tracking-[0.16em] text-lactec-danger-ink">Link indisponível</p>
                <h2 class="display text-2xl font-semibold text-lactec-nav tracking-tight m-0 mt-2">Não foi possível confirmar</h2>
                <p class="text-sm leading-relaxed text-lactec-ink-soft mt-3 mb-0">O link pode ter expirado ou já ter sido utilizado. Entre em contato com o suporte para obter ajuda.</p>
              </div>
              <a mat-stroked-button routerLink="/login" class="w-full h-11">Voltar ao login</a>
            </div>
          } @else {
            <div class="flex flex-col gap-5" role="status" aria-live="polite">
              <mat-icon class="text-lactec-primary-dark" aria-hidden="true">mark_email_unread</mat-icon>
              <div>
                <p class="m-0 text-xs font-semibold uppercase tracking-[0.16em] text-lactec-primary-dark">Confirmação de cadastro</p>
                <h2 class="display text-2xl font-semibold text-lactec-nav tracking-tight m-0 mt-2">Confirmando seu e-mail</h2>
                <p class="text-sm leading-relaxed text-lactec-ink-soft mt-3 mb-0">Aguarde enquanto validamos o link de confirmação.</p>
              </div>
            </div>
          }
        </main>
        <footer class="text-center text-lactec-muted text-xs flex flex-col items-center gap-2">
          <span>Precisa de ajuda? Entre em contato com o suporte.</span>
          <small class="text-xs tracking-widest text-lactec-faint uppercase">© 2026 LACTEC LABORATORY FRAMEWORK. ALL RIGHTS RESERVED.</small>
        </footer>
      </section>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailConfirmationComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly recovery = inject(PasswordRecoveryService);
  readonly state = signal<'loading' | 'success' | 'error'>('loading');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('error');
      return;
    }
    this.recovery.confirmEmail(token).subscribe({
      next: () => this.state.set('success'),
      error: () => this.state.set('error'),
    });
  }
}
