import { BreakpointObserver } from '@angular/cdk/layout';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { NgClass } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { LogoComponent } from '@shared/components/logo/logo.component';
import { LoginService } from '@/app/services/login.service';
import { TourService } from '@/app/services/tour.service';
import { UserRole } from '@core/store/user-role.store';
import { backTargetFor, type BackTarget } from './parent-path';
import { MENU_TOUR_TARGET, menuTour, pageTourFor } from './shell.tour';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  exact: boolean;
  tourTarget: string;
}

const COLLAPSED_KEY = 'lactec.internal.shell.collapsed';
const HANDSET_QUERY = '(max-width: 767.98px)';

function loadCollapsed(): boolean {
  if (typeof localStorage === 'undefined') return false;
  return localStorage.getItem(COLLAPSED_KEY) === '1';
}

const ADMIN_NAV: readonly NavItem[] = [
  { label: 'Dashboard', path: '/admin', icon: 'dashboard', exact: true, tourTarget: MENU_TOUR_TARGET.dashboard },
  { label: 'Orçamentos', path: '/admin/orcamentos', icon: 'description', exact: false, tourTarget: MENU_TOUR_TARGET.quotes },
  { label: 'Técnicos', path: '/admin/tecnicos', icon: 'engineering', exact: false, tourTarget: MENU_TOUR_TARGET.technicians },
  { label: 'Empresas', path: '/admin/empresas', icon: 'business', exact: false, tourTarget: MENU_TOUR_TARGET.companies },
  { label: 'Clientes', path: '/admin/clientes', icon: 'groups', exact: false, tourTarget: MENU_TOUR_TARGET.clients },
];

const TECHNICIAN_NAV: readonly NavItem[] = [
  { label: 'Dashboard', path: '/tecnico', icon: 'dashboard', exact: true, tourTarget: MENU_TOUR_TARGET.dashboard },
  { label: 'Orçamentos', path: '/tecnico/orcamentos', icon: 'description', exact: false, tourTarget: MENU_TOUR_TARGET.quotes },
  { label: 'Manter Ensaio', path: '/tecnico/ensaios', icon: 'science', exact: false, tourTarget: MENU_TOUR_TARGET.tests },
  { label: 'Pesquisar Relatório', path: '/tecnico/relatorios', icon: 'description', exact: false, tourTarget: MENU_TOUR_TARGET.reports },
  { label: 'Equipamentos', path: '/tecnico/equipamentos', icon: 'precision_manufacturing', exact: false, tourTarget: MENU_TOUR_TARGET.equipment },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    NgClass,
    ScrollingModule,
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
  styleUrl: './shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly router = inject(Router);
  private readonly loginService = inject(LoginService);
  private readonly userRole = inject(UserRole);
  private readonly tour = inject(TourService);
  private readonly drawer = viewChild(MatSidenav);

  protected readonly helpOpen = signal(false);
  protected readonly tourTarget = MENU_TOUR_TARGET;
  protected readonly tourActive = this.tour.active;

  readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(loadCollapsed());
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  readonly backTarget = computed<BackTarget | null>(() => backTargetFor(this.currentUrl()));
  protected readonly pageHasTour = computed(() => pageTourFor(this.currentUrl()) !== null);
  readonly nav = computed(() =>
    this.userRole.role() === 'ADMIN' ? ADMIN_NAV : TECHNICIAN_NAV,
  );

  constructor() {
    effect(() => {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(COLLAPSED_KEY, this.collapsed() ? '1' : '0');
    });

    effect(() => {
      this.isHandset();
      untracked(() => this.tour.layoutChanged());
    });

    inject(DestroyRef).onDestroy(
      this.tour.registerHost({
        isHandset: () => this.isHandset(),
        openMenu: () => this.setMenuOpened(true),
        closeMenu: () => this.setMenuOpened(false),
      }),
    );
  }

  toggleCollapse(): void {
    if (this.tourActive()) return;
    this.collapsed.update((collapsed) => !collapsed);
  }

  protected toggleHelp(): void {
    if (this.tourActive()) return;
    this.helpOpen.update((open) => !open);
  }

  protected chooseHelp(topic: 'menu' | 'page'): void {
    this.helpOpen.set(false);

    if (topic === 'menu') {
      const role = this.userRole.role();
      if (role) void this.tour.start(() => menuTour(role));
      return;
    }

    const definition = pageTourFor(this.currentUrl());
    if (definition) void this.tour.start(() => definition);
  }

  private async setMenuOpened(opened: boolean): Promise<void> {
    const drawer = this.drawer();
    if (!drawer || drawer.opened === opened) return;
    await (opened ? drawer.open() : drawer.close());
  }

  logout(): void {
    if (this.tourActive()) return;
    if (this.isHandset()) void this.drawer()?.close();
    this.loginService.logout();
    void this.router.navigateByUrl('/login');
  }
}
