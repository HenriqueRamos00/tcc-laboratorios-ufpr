import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { sections } from '@/app/pages/admin/directory/sections';
import { LoginService } from '@/app/services/login.service';
import { LogoComponent } from '@shared/components/logo/logo.component';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  exact: boolean;
}

const COLLAPSED_KEY = 'lactec.auth2.shell.collapsed';
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
  private readonly loginService = inject(LoginService);

  readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(loadCollapsed());

  readonly nav: readonly NavItem[] = [
    { label: 'Visão geral', path: '/admin', icon: 'dashboard', exact: true },
    ...sections.map(({ label, path, icon }) => ({ label, path: '/admin/' + path, icon, exact: false })),
  ];

  constructor() {
    const dialog = inject(MatDialog);
    inject(DestroyRef).onDestroy(() => dialog.closeAll());
    effect(() => {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(COLLAPSED_KEY, this.collapsed() ? '1' : '0');
    });
  }

  toggleCollapse(): void {
    this.collapsed.update((collapsed) => !collapsed);
  }

  logout(): void {
    this.loginService.signOut();
  }
}
