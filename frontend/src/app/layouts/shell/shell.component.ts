import { BreakpointObserver } from '@angular/cdk/layout';
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

import { AcceptanceDraftService } from '@/app/services/acceptance-draft.service';
import { LoginService } from '@/app/services/login.service';
import { TourService } from '@/app/services/tour.service';

import { backTargetFor } from './parent-path';
import { MENU_TOUR_TARGET, menuTour } from './shell.tour';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  exact: boolean;
  tourTarget: string;
}

type HelpTopic = 'menu' | 'page';

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
  private readonly acceptanceDrafts = inject(AcceptanceDraftService);
  private readonly tour = inject(TourService);

  private readonly drawer = viewChild(MatSidenav);
  protected readonly helpOpen = signal(false);

  protected readonly tourTarget = MENU_TOUR_TARGET;
  protected readonly tourActive = this.tour.active;
  protected readonly pageHasTour = this.tour.hasPageTour;

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
    {
      label: 'Orçamentos',
      path: '/cliente/quotes',
      icon: 'description',
      exact: false,
      tourTarget: MENU_TOUR_TARGET.quotes,
    },
    {
      label: 'Relatórios',
      path: '/cliente/relatorios',
      icon: 'science',
      exact: false,
      tourTarget: MENU_TOUR_TARGET.reports,
    },
    {
      label: 'Equipamentos',
      path: '/cliente/equipamentos',
      icon: 'settings',
      exact: false,
      tourTarget: MENU_TOUR_TARGET.equipment,
    },
  ];

  constructor() {
    effect(() => {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(COLLAPSED_KEY, this.collapsed() ? '1' : '0');
    });

    effect(() => {
      const user = this.loginService.currentIdentity();
      if (user) this.acceptanceDrafts.keepOnlyValidDraftsOf(user.id);
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
    this.collapsed.update((recolhido) => !recolhido);
  }

  protected toggleHelp(): void {
    if (this.tourActive()) return;
    this.helpOpen.update((open) => !open);
  }

  protected chooseHelp(topic: HelpTopic): void {
    this.helpOpen.set(false);
    if (topic === 'menu') void this.tour.start(menuTour);
    if (topic === 'page') void this.tour.startPage();
  }

  private async setMenuOpened(opened: boolean): Promise<void> {
    const drawer = this.drawer();
    if (!drawer || drawer.opened === opened) return;
    await (opened ? drawer.open() : drawer.close());
  }

  logout(): void {
    if (this.tourActive()) return;
    if (this.isHandset()) void this.drawer()?.close();
    this.acceptanceDrafts.clear();
    this.loginService.logout();
    this.router.navigateByUrl('/login');
  }
}
