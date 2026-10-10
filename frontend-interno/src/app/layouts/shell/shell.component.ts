import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { LogoComponent } from '@shared/components/logo/logo.component';
import { LoginService } from '@/app/services/login.service';
import { UserRole } from '@core/store/user-role.store';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  exact: boolean;
}

interface SecondaryAction {
  label: string;
  icon: string;
  action: 'help' | 'profile';
}

const COLLAPSED_KEY = 'lactec.internal.shell.collapsed';
const HANDSET_QUERY = '(max-width: 767.98px)';

function loadCollapsed(): boolean {
  if (typeof localStorage === 'undefined') return false;
  return localStorage.getItem(COLLAPSED_KEY) === '1';
}

const ADMIN_NAV: readonly NavItem[] = [
  { label: 'Dashboard', path: '/admin', icon: 'dashboard', exact: true },
  { label: 'Orçamentos', path: '/admin/orcamentos', icon: 'description', exact: false },
  { label: 'Técnicos', path: '/admin/tecnicos', icon: 'engineering', exact: false },
  { label: 'Empresas', path: '/admin/empresas', icon: 'business', exact: false },
  { label: 'Clientes', path: '/admin/clientes', icon: 'groups', exact: false },
];

const TECHNICIAN_NAV: readonly NavItem[] = [
  { label: 'Dashboard', path: '/tecnico', icon: 'dashboard', exact: true },
  { label: 'Pesquisar Ensaios e Amostras', path: '/tecnico/ensaios-amostras', icon: 'search', exact: false },
  { label: 'Manter Ensaio', path: '/tecnico/ensaios', icon: 'science', exact: false },
  { label: 'Resultados', path: '/tecnico/resultados', icon: 'fact_check', exact: false },
  { label: 'Pesquisar Relatório', path: '/tecnico/relatorios', icon: 'description', exact: false },
  { label: 'Equipamentos', path: '/tecnico/equipamentos', icon: 'precision_manufacturing', exact: false },
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
  private readonly loginService = inject(LoginService);
  private readonly userRole = inject(UserRole);

  readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(loadCollapsed());
  readonly nav = computed(() =>
    this.userRole.role() === 'ADMIN' ? ADMIN_NAV : TECHNICIAN_NAV,
  );

  readonly bottomActions: readonly SecondaryAction[] = [
    { label: 'Ajuda', icon: 'help_outline', action: 'help' },
    { label: 'Perfil', icon: 'person_outline', action: 'profile' },
  ];

  constructor() {
    effect(() => {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(COLLAPSED_KEY, this.collapsed() ? '1' : '0');
    });
  }

  toggleCollapse(): void {
    this.collapsed.update((collapsed) => !collapsed);
  }

  onSecondaryAction(action: SecondaryAction['action']): void {
    // As ações de Ajuda e Perfil aguardam implementação, como no Portal do Cliente.
    void action;
  }

  logout(): void {
    this.loginService.logout();
    void this.router.navigateByUrl('/login');
  }
}
