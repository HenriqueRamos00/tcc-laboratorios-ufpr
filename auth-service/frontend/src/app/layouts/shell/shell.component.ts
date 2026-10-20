import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize, map } from 'rxjs';

import { AdminSessionStore } from '@core/store/admin-session.store';
import { LogoComponent } from '@shared/components/logo/logo.component';
import { AdminAuthService } from '@/app/services/admin-auth.service';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  exact: boolean;
}

const COLLAPSED_KEY = 'lactec.auth.admin.shell.collapsed';
const HANDSET_QUERY = '(max-width: 767.98px)';

function loadCollapsed(): boolean {
  if (typeof localStorage === 'undefined') return false;
  return localStorage.getItem(COLLAPSED_KEY) === '1';
}

const NAV: readonly NavItem[] = [
  { label: 'Usuários', path: '/admin/users', icon: 'groups', exact: false },
  { label: 'Empresas', path: '/admin/companies', icon: 'business', exact: false },
  { label: 'Integrações', path: '/admin/api-keys', icon: 'vpn_key', exact: false },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    NgClass,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    LogoComponent,
  ],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly router = inject(Router);
  private readonly auth = inject(AdminAuthService);
  readonly session = inject(AdminSessionStore);

  readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(loadCollapsed());
  readonly nav = NAV;

  constructor() {
    effect(() => {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(COLLAPSED_KEY, this.collapsed() ? '1' : '0');
    });
  }

  toggleCollapse(): void {
    this.collapsed.update((collapsed) => !collapsed);
  }

  logout(): void {
    this.auth
      .logout()
      .pipe(
        finalize(() => {
          this.session.clear();
          void this.router.navigateByUrl('/login');
        }),
      )
      .subscribe({ error: () => undefined });
  }
}
