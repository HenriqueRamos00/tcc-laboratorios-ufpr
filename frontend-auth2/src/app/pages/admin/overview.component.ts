import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Application } from '@/app/model/access';
import { AccessService, accessError } from '@/app/services/access.service';
import { LoginService } from '@/app/services/login.service';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { accessBreadcrumb } from './directory/sections';

interface Overview {
  clientCount: number;
  adminCount: number;
  applicationCount: number;
  activeApplicationCount: number;
  topApplications: (Application & { clientCount: number })[];
}

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatIconModule, PageHeaderComponent, RouterLink],
  templateUrl: './overview.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewComponent {
  private readonly api = inject(AccessService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly user = inject(LoginService).currentUser;
  protected readonly breadcrumbs = [accessBreadcrumb, { label: 'Visão geral' }];
  protected readonly overview = signal<Overview | null>(null);
  protected readonly error = signal('');

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set('');
    forkJoin({
      applications: this.api.list('services'),
      clients: this.api.list('users'),
      admins: this.api.list('admins'),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ applications, clients, admins }) => {
        const clientsByApplication = new Map<string, number>();
        for (const { serviceId } of clients) {
          clientsByApplication.set(serviceId, (clientsByApplication.get(serviceId) ?? 0) + 1);
        }

        const ranked = applications.map(application => ({
          ...application,
          clientCount: clientsByApplication.get(application.id) ?? 0,
        })).sort((a, b) => b.clientCount - a.clientCount || a.name.localeCompare(b.name, 'pt-BR'));

        this.overview.set({
          clientCount: clients.length,
          adminCount: admins.length,
          applicationCount: applications.length,
          activeApplicationCount: applications.filter(application => application.active).length,
          topApplications: ranked.slice(0, 5),
        });
      },
      error: (error: unknown) => {
        this.error.set(accessError(error, 'Não foi possível carregar a visão geral.'));
      },
    });
  }
}
