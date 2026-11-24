import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { LoginService } from '@/app/services/login.service';

@Component({
  selector: 'app-overview',
  standalone: true,
  template: `
    @if (user(); as administrator) {
      <section class="rounded-xl border border-lactec-line bg-lactec-paper p-6 sm:p-8">
        <h1 class="font-display text-2xl font-semibold tracking-tight sm:text-3xl">
          Olá, {{ administrator.name }}
        </h1>
        <p class="mt-2 wrap-anywhere text-lactec-muted">{{ administrator.email }}</p>
      </section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewComponent {
  protected readonly user = inject(LoginService).currentUser;
}
