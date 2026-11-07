import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { LogoComponent } from '@shared/components/logo/logo.component';

import { LoginService } from '@/app/services/login.service';

import { backTargetFor } from './parent-path';

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

const COLLAPSED_KEY = 'lactec.shell.collapsed';
const HANDSET_QUERY = '(max-width: 767.98px)';

function loadCollapsed(): boolean {
  if (typeof localStorage === 'undefined') return false;
  return localStorage.getItem(COLLAPSED_KEY) === '1';
}

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

  readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(loadCollapsed());

  // O endereco corrente, e nao o historico, e quem dita o retorno. O shell nao e recriado a
  // cada navegacao, entao ele acompanha o evento do roteador em vez de ler a URL uma vez.
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
      map((evento) => evento.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  readonly backTarget = computed(() => backTargetFor(this.currentUrl()));

  readonly nav: readonly NavItem[] = [
    { label: 'Orçamentos', path: '/cliente/quotes', icon: 'description', exact: false },
    { label: 'Relatórios', path: '/cliente/relatorios', icon: 'science', exact: false },
    { label: 'Equipamentos', path: '/cliente/equipamentos', icon: 'settings', exact: false },
  ];

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
    this.collapsed.update((recolhido) => !recolhido);
  }


  onSecondaryAction(action: SecondaryAction['action']): void {
    void action;
  }

  logout(): void {
    this.loginService.logout();
    this.router.navigateByUrl('/login');
  }
}
