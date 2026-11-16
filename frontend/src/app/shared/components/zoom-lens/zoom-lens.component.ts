import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  NgZone,
  signal,
  viewChild,
} from '@angular/core';

export const LENS_SIZE = 144;
export const LENS_BORDER = 4;
export const INITIAL_ZOOM = 2.5;
export const MIN_ZOOM = 1.5;
export const MAX_ZOOM = 20;
export const ZOOM_FACTOR = 1.25;
export const LONG_PRESS_MS = 450;
export const HINT_VISIBLE_MS = 5000;
const LONG_PRESS_TOLERANCE = 10;
const ACTIVE_CLASSES = ['lens-open', 'cursor-none', '[&_*]:cursor-none!'];

/**
 * Lupa para um diagrama em SVG. Segurar o botão direito sobre o desenho abre uma lente
 * redonda que acompanha o cursor e mostra uma cópia vetorial ampliada do SVG, sem perder
 * nitidez. A roda do mouse muda o aumento enquanto o botão está seguro, e soltar fecha a
 * lente e volta ao aumento inicial. No toque, segurar o dedo parado abre a lente, arrastar
 * move e levantar fecha. O menu de contexto do navegador fica bloqueado só sobre o desenho.
 */
@Component({
  selector: 'app-zoom-lens',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div #area class="group relative select-none [-webkit-touch-callout:none]" (mouseenter)="showHintBriefly()">
      <ng-content />
      <div
        #hint
        data-zoom-hint
        [attr.data-expanded]="hintExpanded()"
        class="absolute top-0 right-0 z-10 flex items-center rounded-lg border transition-all duration-500 ease-in-out
               group-[.lens-open]:pointer-events-none group-[.lens-open]:opacity-0 motion-reduce:transition-none"
        [class]="hintExpanded()
          ? 'border-lactec-line bg-lactec-paper/95 shadow-md'
          : 'border-transparent bg-transparent shadow-none'"
      >
        <div
          role="note"
          class="flex items-center gap-2 overflow-hidden whitespace-nowrap text-[10px] leading-tight text-lactec-ink-soft
                 transition-all duration-500 ease-in-out motion-reduce:transition-none"
          [class]="hintExpanded() ? 'max-w-64 py-1.5 pl-1 opacity-100' : 'max-w-0 py-0 pl-0 opacity-0'"
          [inert]="!hintExpanded()"
        >
          <button
            type="button"
            aria-label="Fechar dica de ampliação"
            data-zoom-hint-close
            class="flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center rounded-full text-lactec-muted
                   hover:bg-lactec-line-soft hover:text-lactec-ink"
            (click)="closeHint()"
          >
            <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
              <path d="M2 2 L8 8 M8 2 L2 8" class="stroke-current" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </button>
          <svg width="16" height="24" viewBox="0 0 16 24" class="hidden shrink-0 [@media(hover:hover)]:block" aria-hidden="true">
            <path d="M8 1 A7 7 0 0 1 15 8 V10 H8 Z" class="fill-lactec-primary" />
            <rect x="1" y="1" width="14" height="22" rx="7" class="fill-none stroke-lactec-ink-soft" stroke-width="1.5" />
            <line x1="1" y1="10" x2="15" y2="10" class="stroke-lactec-ink-soft" stroke-width="1.2" />
            <line x1="8" y1="1" x2="8" y2="10" class="stroke-lactec-ink-soft" stroke-width="1.2" />
            <circle cx="8" cy="7" r="1.4" class="wheel fill-lactec-ink motion-reduce:animate-none" />
          </svg>
          <span class="hidden [@media(hover:hover)]:inline">
            Segure o botão direito e<br />role para cima para aumentar o zoom
          </span>
          <span class="[@media(hover:hover)]:hidden">Toque e segure<br />o diagrama para ampliar</span>
        </div>
        <button
          type="button"
          aria-label="Como ampliar o diagrama"
          [attr.aria-expanded]="hintExpanded()"
          data-zoom-hint-open
          class="flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-lactec-primary
                 hover:bg-lactec-line-soft"
          (click)="pinHint()"
        >
          <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
            <circle cx="10" cy="10" r="8.5" class="fill-none stroke-current" stroke-width="1.8" />
            <line x1="10" y1="9" x2="10" y2="14.5" class="stroke-current" stroke-width="2" stroke-linecap="round" />
            <circle cx="10" cy="5.8" r="1.2" class="fill-current" />
          </svg>
        </button>
      </div>
      <div
        #lens
        data-zoom-lens
        aria-hidden="true"
        inert
        class="pointer-events-none absolute z-20 hidden -translate-x-1/2 -translate-y-1/2 overflow-hidden
               rounded-full border-lactec-paper bg-lactec-paper
               shadow-[0_0_30px_rgba(17,17,17,0.45),inset_0_0_4px_rgba(17,17,17,0.4)]"
        [style.width.px]="LENS_SIZE"
        [style.height.px]="LENS_SIZE"
        [style.border-width.px]="LENS_BORDER"
      >
        <div #stage class="absolute"></div>
      </div>
    </div>
  `,
  styles: `
    @keyframes wheel-scroll {
      0% { opacity: 1; transform: translateY(0); }
      15% { opacity: 1; transform: translateY(-2.5px); }
      50% { opacity: 0; transform: translateY(-2.5px); }
      100% { opacity: 0; transform: translateY(0); }
    }
    .wheel { animation: wheel-scroll 2.5s linear infinite; }
  `,
})
export class ZoomLensComponent {
  protected readonly LENS_SIZE = LENS_SIZE;
  protected readonly LENS_BORDER = LENS_BORDER;

  protected readonly hintExpanded = signal(false);

  private readonly area = viewChild.required<ElementRef<HTMLElement>>('area');
  private readonly hint = viewChild.required<ElementRef<HTMLElement>>('hint');
  private readonly lens = viewChild.required<ElementRef<HTMLElement>>('lens');
  private readonly stage = viewChild.required<ElementRef<HTMLElement>>('stage');

  private source: SVGSVGElement | null = null;
  private zoom = INITIAL_ZOOM;
  private pointer = { x: 0, y: 0 };
  private frame = 0;
  private pressTimer: ReturnType<typeof setTimeout> | null = null;
  private pressStart = { x: 0, y: 0 };
  private readonly stopListening: (() => void)[] = [];
  private hintTimer: ReturnType<typeof setTimeout> | null = null;
  private hintPinned = false;

  constructor() {
    const zone = inject(NgZone);
    inject(DestroyRef).onDestroy(() => {
      this.cancelHintTimer();
      this.close();
      this.stopListening.forEach((stop) => stop());
    });
    afterNextRender(() => zone.runOutsideAngular(() => this.listen()));
  }

  protected showHintBriefly(): void {
    if (this.hintPinned) return;
    this.hintExpanded.set(true);
    this.cancelHintTimer();
    this.hintTimer = setTimeout(() => {
      this.hintTimer = null;
      if (!this.hintPinned) this.hintExpanded.set(false);
    }, HINT_VISIBLE_MS);
  }

  protected pinHint(): void {
    this.cancelHintTimer();
    this.hintPinned = true;
    this.hintExpanded.set(true);
  }

  protected closeHint(): void {
    this.cancelHintTimer();
    this.hintPinned = false;
    this.hintExpanded.set(false);
  }

  private cancelHintTimer(): void {
    if (this.hintTimer) clearTimeout(this.hintTimer);
    this.hintTimer = null;
  }

  get isOpen(): boolean {
    return this.source !== null;
  }

  get currentZoom(): number {
    return this.zoom;
  }

  private listen(): void {
    const area = this.area().nativeElement;
    const on = <K extends keyof HTMLElementEventMap>(
      target: HTMLElement | Window,
      type: K,
      handler: (event: HTMLElementEventMap[K]) => void,
    ) => {
      target.addEventListener(type, handler as EventListener, { passive: false });
      this.stopListening.push(() => target.removeEventListener(type, handler as EventListener));
    };

    on(area, 'contextmenu', (event) => {
      if (this.isOpen || this.diagramUnder(event.target)) event.preventDefault();
    });
    on(area, 'mousedown', (event) => {
      if (event.button !== 2) return;
      const diagram = this.diagramUnder(event.target);
      if (!diagram) return;
      event.preventDefault();
      this.open(diagram, event.clientX, event.clientY);
    });
    on(window, 'mousemove', (event) => {
      if (!this.isOpen) return;
      if ((event.buttons & 2) === 0) return this.close();
      this.moveTo(event.clientX, event.clientY);
    });
    on(window, 'mouseup', (event) => {
      if (this.isOpen && event.button === 2) this.close();
    });
    on(area, 'wheel', (event) => {
      if (!this.isOpen) return;
      event.preventDefault();
      const factor = event.deltaY < 0 ? ZOOM_FACTOR : event.deltaY > 0 ? 1 / ZOOM_FACTOR : 1;
      this.zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, this.zoom * factor));
      this.render();
    });
    on(area, 'touchstart', (event) => {
      this.cancelPress();
      if (event.touches.length !== 1) return this.close();
      const diagram = this.diagramUnder(event.target);
      if (!diagram) return;
      const touch = event.touches[0];
      this.pressStart = { x: touch.clientX, y: touch.clientY };
      this.pressTimer = setTimeout(() => {
        this.pressTimer = null;
        this.open(diagram, this.pressStart.x, this.pressStart.y);
      }, LONG_PRESS_MS);
    });
    on(area, 'touchmove', (event) => {
      const touch = event.touches[0];
      if (!touch) return;
      if (this.isOpen) {
        event.preventDefault();
        this.moveTo(touch.clientX, touch.clientY);
      } else if (
        Math.hypot(touch.clientX - this.pressStart.x, touch.clientY - this.pressStart.y) > LONG_PRESS_TOLERANCE
      ) {
        this.cancelPress();
      }
    });
    const endTouch = (event: TouchEvent) => {
      this.cancelPress();
      if (!this.isOpen) return;
      event.preventDefault();
      this.close();
    };
    on(area, 'touchend', endTouch);
    on(area, 'touchcancel', endTouch);
  }

  private diagramUnder(target: EventTarget | null): SVGSVGElement | null {
    if (
      !(target instanceof Element)
      || this.lens().nativeElement.contains(target)
      || this.hint().nativeElement.contains(target)
    )
      return null;
    const diagram = target.closest('svg');
    return diagram && this.area().nativeElement.contains(diagram) ? diagram : null;
  }

  private open(diagram: SVGSVGElement, x: number, y: number): void {
    this.source = diagram;
    this.zoom = INITIAL_ZOOM;
    this.lens().nativeElement.classList.remove('hidden');
    this.area().nativeElement.classList.add(...ACTIVE_CLASSES);
    this.pointer = { x, y };
    this.render();
  }

  private moveTo(x: number, y: number): void {
    this.pointer = { x, y };
    if (this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      this.render();
    });
  }

  private render(): void {
    const source = this.source;
    if (!source) return;
    const areaBox = this.area().nativeElement.getBoundingClientRect();
    const box = source.getBoundingClientRect();
    const inner = LENS_SIZE - 2 * LENS_BORDER;
    const lens = this.lens().nativeElement;
    const stage = this.stage().nativeElement;

    lens.style.left = `${this.pointer.x - areaBox.left}px`;
    lens.style.top = `${this.pointer.y - areaBox.top}px`;
    stage.style.left = `${inner / 2 - (this.pointer.x - box.left) * this.zoom}px`;
    stage.style.top = `${inner / 2 - (this.pointer.y - box.top) * this.zoom}px`;
    stage.style.width = `${box.width * this.zoom}px`;
    stage.style.height = `${box.height * this.zoom}px`;

    const copy = source.cloneNode(true) as SVGSVGElement;
    for (const attribute of ['class', 'role', 'aria-label', 'width', 'height']) copy.removeAttribute(attribute);
    copy.querySelectorAll('[tabindex]').forEach((element) => element.removeAttribute('tabindex'));
    copy.querySelectorAll('circle').forEach((point) => {
      for (const attribute of ['r', 'stroke-width']) {
        const size = Number.parseFloat(point.getAttribute(attribute) ?? '');
        if (Number.isFinite(size)) point.setAttribute(attribute, String(size / this.zoom));
      }
    });
    copy.style.opacity = getComputedStyle(source).opacity;
    copy.style.display = 'block';
    copy.style.width = '100%';
    copy.style.height = '100%';
    stage.replaceChildren(copy);
  }

  private close(): void {
    this.cancelPress();
    if (this.frame) cancelAnimationFrame(this.frame);
    this.frame = 0;
    this.source = null;
    this.zoom = INITIAL_ZOOM;
    this.lens().nativeElement.classList.add('hidden');
    this.area().nativeElement.classList.remove(...ACTIVE_CLASSES);
    this.stage().nativeElement.replaceChildren();
  }

  private cancelPress(): void {
    if (this.pressTimer) clearTimeout(this.pressTimer);
    this.pressTimer = null;
  }
}
