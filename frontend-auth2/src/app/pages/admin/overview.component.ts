import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { LoginService } from '@/app/services/login.service';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [MatIconModule, PageHeaderComponent],
  template: `
    @if (user(); as administrator) {
      <div class="space-y-8 wrap-anywhere">
        <app-page-header
          [title]="'Olá, ' + administrator.name"
          subtitle="Visão geral da administração do Auth2"
          [breadcrumbs]="breadcrumbs"
        />

        <section
          class="max-w-xl min-w-0 rounded-xl border border-lactec-line bg-lactec-paper p-5 shadow-sm"
          aria-labelledby="overview-account"
        >
          <div class="mb-5 flex min-w-0 items-start justify-between gap-3">
            <h2
              id="overview-account"
              class="m-0 min-w-0 text-sm font-medium uppercase leading-snug text-lactec-muted"
            >
              Conta conectada
            </h2>
            <mat-icon class="shrink-0 text-lactec-faint" aria-hidden="true">person_outline</mat-icon>
          </div>
          <p class="m-0 font-semibold leading-tight text-lactec-ink">{{ administrator.name }}</p>
          <p class="mt-1 mb-0 text-sm text-lactec-muted">{{ administrator.email }}</p>
        </section>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewComponent {
  protected readonly user = inject(LoginService).currentUser;
  protected readonly breadcrumbs = [
    { label: 'Administração Auth2', route: '/admin' },
    { label: 'Visão geral' },
  ] as const;
}
