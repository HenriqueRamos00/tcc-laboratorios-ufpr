import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import type { Driver, Popover } from 'driver.js';
import { filter, fromEvent, merge, Subscription } from 'rxjs';

import {
  regionOf,
  tourSelector,
  type TourDefinition,
  type TourFactory,
  type TourHost,
  type TourScene,
  type TourStep,
} from '@/app/model/tour';

const POPOVER_CLASS = 'lactec-tour';
const STALE_POPOVER_CLASS = 'lactec-tour--stale';
const OVERLAY_COLOR_TOKEN = '--color-lactec-ink';
const OVERLAY_OPACITY = 0.6;
const STAGE_PADDING = 6;
const STAGE_RADIUS = 8;
const VIEW_MARGIN = 16;
const RESYNC_DELAY_MILLISECONDS = 120;
const SCENE_WAIT_MILLISECONDS = 600;
const SCENE_POLL_MILLISECONDS = 50;

const NOTHING_TO_SHOW = {
  title: 'Nada para destacar agora',
  text: 'Esta página ainda está carregando ou não tem conteúdo para explicar no momento. Abra a Ajuda de novo em instantes.',
};

interface TourRun {
  readonly definition: TourDefinition;
  readonly driver: Driver;
  readonly listeners: Subscription;
  index: number;
  target: Element | null;
  scene: TourScene | null;
  navigation: number;
  observer: MutationObserver | null;
  resyncTimer: ReturnType<typeof setTimeout> | undefined;
  relayout: boolean;
}

function wait(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function isVisible(element: Element): boolean {
  if (!element.isConnected || element.getClientRects().length === 0) return false;
  return getComputedStyle(element).visibility === 'visible';
}

function clipsContent(element: Element): boolean {
  const style = getComputedStyle(element);
  return style.overflowX !== 'visible' || style.overflowY !== 'visible';
}

@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);

  private readonly running = signal<string | null>(null);
  private readonly pageFactory = signal<TourFactory | null>(null);

  readonly active = computed(() => this.running() !== null);
  readonly hasPageTour = computed(() => this.pageFactory() !== null);

  private host: TourHost | null = null;
  private run: TourRun | null = null;
  private startRequest = 0;

  registerHost(host: TourHost): () => void {
    this.host = host;
    return () => {
      if (this.host !== host) return;
      this.stop();
      this.host = null;
    };
  }

  registerPage(factory: TourFactory): () => void {
    this.pageFactory.set(factory);
    return () => {
      if (this.pageFactory() !== factory) return;
      this.stop();
      this.pageFactory.set(null);
    };
  }

  startPage(): Promise<void> {
    const factory = this.pageFactory();
    return factory ? this.start(factory) : Promise.resolve();
  }

  async start(factory: TourFactory): Promise<void> {
    this.stop();
    const request = ++this.startRequest;
    const definition = factory();

    const { driver } = await import('driver.js');
    if (request !== this.startRequest) return;

    const run: TourRun = {
      definition,
      driver: driver({
        animate: !this.prefersReducedMotion(),
        smoothScroll: false,
        allowClose: true,
        overlayClickBehavior: 'none',
        disableActiveInteraction: true,
        overlayColor: this.overlayColor(),
        overlayOpacity: OVERLAY_OPACITY,
        stagePadding: STAGE_PADDING,
        stageRadius: STAGE_RADIUS,
        popoverClass: POPOVER_CLASS,
        closeBtnLabel: 'Fechar ajuda',
        onDestroyed: () => this.finish(run),
      }),
      listeners: new Subscription(),
      index: 0,
      target: null,
      scene: null,
      navigation: 0,
      observer: null,
      resyncTimer: undefined,
      relayout: false,
    };

    this.run = run;
    this.running.set(definition.id);
    this.listen(run);

    const shown = await this.seek(run, 0, 1);
    if (!shown && this.run === run) this.showNothing(run);
  }

  stop(): void {
    this.startRequest++;
    const run = this.run;
    if (!run) return;
    this.finish(run);
    if (run.driver.isActive()) run.driver.destroy();
  }

  layoutChanged(): void {
    const run = this.run;
    if (!run) return;
    clearTimeout(run.resyncTimer);
    if (run.target && !isVisible(run.target)) this.popover()?.classList.add(STALE_POPOVER_CLASS);
    run.resyncTimer = setTimeout(() => void this.resync(run), RESYNC_DELAY_MILLISECONDS);
  }

  private finish(run: TourRun): void {
    if (this.run !== run) return;
    this.run = null;
    run.navigation++;
    clearTimeout(run.resyncTimer);
    run.observer?.disconnect();
    run.listeners.unsubscribe();
    run.scene?.leave();
    run.scene = null;
    this.running.set(null);
  }

  private listen(run: TourRun): void {
    const view = this.document.defaultView;

    run.listeners.add(
      this.router.events
        .pipe(filter((event) => event instanceof NavigationStart))
        .subscribe(() => this.stop()),
    );

    if (!view) return;

    run.listeners.add(
      merge(fromEvent(view, 'resize'), fromEvent(view, 'orientationchange')).subscribe(() =>
        this.layoutChanged(),
      ),
    );

    run.listeners.add(
      fromEvent<KeyboardEvent>(this.document, 'keydown').subscribe((event) => {
        if (event.repeat || event.altKey || event.ctrlKey || event.metaKey) return;
        if (event.key === 'ArrowRight') void this.next(run);
        if (event.key === 'ArrowLeft') void this.previous(run);
      }),
    );

    if (typeof MutationObserver === 'undefined') return;
    run.observer = new MutationObserver(() => {
      if (run.target && !isVisible(run.target)) this.layoutChanged();
    });
    run.observer.observe(this.document.body, { childList: true, subtree: true });
  }

  private async next(run: TourRun): Promise<void> {
    if (this.run !== run) return;
    const moved = await this.seek(run, run.index + 1, 1);
    if (!moved) this.stop();
  }

  private async previous(run: TourRun): Promise<void> {
    if (this.run !== run || run.index === 0) return;
    const moved = await this.seek(run, run.index - 1, -1);
    if (!moved) await this.seek(run, run.index, 1);
  }

  private async resync(run: TourRun): Promise<void> {
    if (this.run !== run) return;
    run.relayout = true;
    try {
      const kept =
        (await this.seek(run, run.index, 1)) || (await this.seek(run, run.index - 1, -1));
      if (!kept) this.stop();
    } finally {
      run.relayout = false;
    }
  }

  private popover(): HTMLElement | null {
    return this.document.querySelector<HTMLElement>(`.driver-popover.${POPOVER_CLASS}`);
  }

  private async seek(run: TourRun, from: number, direction: 1 | -1): Promise<boolean> {
    const navigation = ++run.navigation;
    const steps = run.definition.steps;

    for (let index = from; index >= 0 && index < steps.length; index += direction) {
      const step = steps[index];
      if (step.scene?.available?.() === false) continue;

      const entered = await this.stage(run, step);
      if (run.navigation !== navigation) return true;

      const target = await this.awaitTarget(run, step, navigation, entered);
      if (run.navigation !== navigation) return true;

      if (target) {
        this.highlight(run, index, target);
        return true;
      }
    }

    return false;
  }

  private async stage(run: TourRun, step: TourStep): Promise<boolean> {
    const scene = step.scene ?? null;
    const entered = scene !== null && run.scene !== scene;
    if (run.scene !== scene) {
      run.scene?.leave();
      run.scene = scene;
      await scene?.enter();
    }

    const host = this.host;
    if (host?.isHandset()) {
      if (regionOf(step) === 'menu') await host.openMenu();
      else await host.closeMenu();
    }

    return entered;
  }

  private async awaitTarget(
    run: TourRun,
    step: TourStep,
    navigation: number,
    sceneJustEntered: boolean,
  ): Promise<Element | null> {
    const deadline = sceneJustEntered ? Date.now() + SCENE_WAIT_MILLISECONDS : 0;

    for (;;) {
      const target = this.findTarget(step);
      if (target || Date.now() >= deadline || run.navigation !== navigation) return target;
      await wait(SCENE_POLL_MILLISECONDS);
    }
  }

  private findTarget(step: TourStep): Element | null {
    const candidates = this.document.querySelectorAll(tourSelector(step.target));
    return Array.from(candidates).find(isVisible) ?? null;
  }

  private isAvailable(run: TourRun, step: TourStep): boolean {
    if (step.scene && step.scene !== run.scene) return step.scene.available?.() ?? true;
    if (regionOf(step) === 'menu') {
      return this.document.querySelector(tourSelector(step.target)) !== null;
    }
    return this.findTarget(step) !== null;
  }

  private highlight(run: TourRun, index: number, target: Element): void {
    const unchanged = run.index === index && run.target === target && run.driver.isActive();
    run.index = index;
    run.target = target;
    this.reveal(target);

    const steps = run.definition.steps;
    const step = steps[index];
    const available = steps.filter(
      (candidate, position) => position === index || this.isAvailable(run, candidate),
    );
    const current = available.indexOf(step) + 1;
    const position: Pick<Popover, 'disableButtons' | 'progressText' | 'nextBtnText'> = {
      disableButtons: current === 1 ? ['previous'] : [],
      progressText: `Passo ${current} de ${available.length}`,
      nextBtnText: current === available.length ? 'Concluir' : 'Próximo',
    };

    if (unchanged) {
      run.driver.refresh();
      run.driver.updatePopover(position);
      this.popover()?.classList.remove(STALE_POPOVER_CLASS);
      return;
    }

    const config = run.driver.getConfig();
    const instant = run.relayout && config.animate !== false;
    if (instant) run.driver.setConfig({ ...config, animate: false });
    run.driver.highlight({
      element: target,
      popover: {
        ...position,
        title: step.title,
        description: step.text,
        side: step.side,
        showButtons: ['previous', 'next', 'close'],
        showProgress: true,
        prevBtnText: 'Anterior',
        onPrevClick: () => void this.previous(run),
        onNextClick: () => void this.next(run),
      },
    });
    if (instant) run.driver.setConfig(config);
    this.popover()?.classList.remove(STALE_POPOVER_CLASS);
  }

  private showNothing(run: TourRun): void {
    run.driver.highlight({
      popover: {
        title: NOTHING_TO_SHOW.title,
        description: NOTHING_TO_SHOW.text,
        showButtons: ['next', 'close'],
        nextBtnText: 'Entendi',
        onNextClick: () => this.stop(),
      },
    });
  }

  private reveal(target: Element): void {
    const view = this.document.defaultView;
    if (!view) return;

    const rect = target.getBoundingClientRect();
    let top = 0;
    let bottom = view.innerHeight;
    let left = 0;
    let right = view.innerWidth;

    for (let parent = target.parentElement; parent; parent = parent.parentElement) {
      if (!clipsContent(parent)) continue;
      const box = parent.getBoundingClientRect();
      top = Math.max(top, box.top);
      bottom = Math.min(bottom, box.bottom);
      left = Math.max(left, box.left);
      right = Math.min(right, box.right);
    }

    const height = bottom - top;
    const fitsVertically =
      rect.top >= top + VIEW_MARGIN && rect.bottom <= bottom - VIEW_MARGIN;
    const fitsHorizontally = rect.left >= left && rect.right <= right;
    if (fitsVertically && fitsHorizontally) return;

    target.scrollIntoView({
      block: rect.height + 2 * VIEW_MARGIN > height ? 'start' : 'center',
      inline: 'nearest',
      behavior: 'instant',
    });
  }

  private prefersReducedMotion(): boolean {
    const view = this.document.defaultView;
    return view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }

  private overlayColor(): string {
    const view = this.document.defaultView;
    const color = view
      ?.getComputedStyle(this.document.documentElement)
      .getPropertyValue(OVERLAY_COLOR_TOKEN)
      .trim();
    return color || 'CanvasText';
  }
}

export function registerPageTour(factory: TourFactory): void {
  const unregister = inject(TourService).registerPage(factory);
  inject(DestroyRef).onDestroy(unregister);
}
