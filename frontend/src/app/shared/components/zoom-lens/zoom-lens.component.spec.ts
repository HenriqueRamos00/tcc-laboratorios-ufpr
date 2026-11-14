import { Component, viewChild } from '@angular/core';
import { fakeAsync, flush, TestBed, tick } from '@angular/core/testing';

import { HINT_VISIBLE_MS, INITIAL_ZOOM, LONG_PRESS_MS, MAX_ZOOM, ZoomLensComponent, ZOOM_FACTOR } from './zoom-lens.component';

@Component({
  imports: [ZoomLensComponent],
  template: `
    <app-zoom-lens>
      <svg viewBox="0 0 100 100" width="200" height="200" class="max-w-64">
        <circle cx="50" cy="50" r="10" stroke-width="2" tabindex="0" />
      </svg>
      <p class="legenda">Legenda</p>
    </app-zoom-lens>
  `,
})
class LensHost {
  readonly lens = viewChild.required(ZoomLensComponent);
}

describe('ZoomLensComponent', () => {
  function render() {
    TestBed.configureTestingModule({ imports: [LensHost] });
    const fixture = TestBed.createComponent(LensHost);
    fixture.detectChanges();
    TestBed.flushEffects();
    const root = fixture.nativeElement as HTMLElement;
    const diagram = root.querySelector('app-zoom-lens svg') as SVGSVGElement;
    const box = diagram.getBoundingClientRect();
    return {
      fixture,
      root,
      lens: fixture.componentInstance.lens(),
      diagram,
      center: { clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 },
      lensElement: root.querySelector('[data-zoom-lens]') as HTMLElement,
    };
  }

  function mouse(target: Element | Window, type: string, init: MouseEventInit): MouseEvent {
    const event = new MouseEvent(type, { bubbles: true, cancelable: true, ...init });
    target.dispatchEvent(event);
    return event;
  }

  function touch(target: Element, type: string, at: { clientX: number; clientY: number } | null): TouchEvent {
    const touches = at ? [new Touch({ identifier: 1, target, ...at })] : [];
    const event = new TouchEvent(type, { bubbles: true, cancelable: true, touches, changedTouches: touches });
    target.dispatchEvent(event);
    return event;
  }

  it('bloqueia o menu do botão direito só sobre o desenho', () => {
    const { root, diagram } = render();

    expect(mouse(diagram, 'contextmenu', { button: 2 }).defaultPrevented).toBeTrue();
    expect(mouse(root.querySelector('.legenda')!, 'contextmenu', { button: 2 }).defaultPrevented).toBeFalse();
  });

  it('abre a lente com uma cópia do desenho ao segurar o botão direito e fecha ao soltar', () => {
    const { lens, diagram, center, lensElement } = render();

    mouse(diagram, 'mousedown', { button: 2, buttons: 2, ...center });

    expect(lens.isOpen).toBeTrue();
    expect(lensElement.classList).not.toContain('hidden');
    const copy = lensElement.querySelector('svg')!;
    expect(copy).not.toBeNull();
    expect(copy.hasAttribute('class')).toBeFalse();
    expect(copy.querySelector('[tabindex]')).toBeNull();

    mouse(window, 'mouseup', { button: 2, buttons: 0, ...center });

    expect(lens.isOpen).toBeFalse();
    expect(lensElement.classList).toContain('hidden');
    expect(lensElement.querySelector('svg')).toBeNull();
  });

  it('a dica começa recolhida, abre quando o mouse entra no diagrama e recolhe depois de 5 segundos', fakeAsync(() => {
    const { root, fixture } = render();
    const hint = root.querySelector('[data-zoom-hint]')!;
    const expanded = () => hint.getAttribute('data-expanded');

    expect(expanded()).toBe('false');
    expect(hint.querySelector('[role="note"]')!.hasAttribute('inert')).toBeTrue();

    hint.parentElement!.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(expanded()).toBe('true');
    expect(hint.textContent).toContain('role para cima para aumentar o zoom');

    tick(HINT_VISIBLE_MS);
    fixture.detectChanges();
    expect(expanded()).toBe('false');
  }));

  it('o ícone de info deixa a dica aberta até o X', fakeAsync(() => {
    const { root, fixture } = render();
    const hint = root.querySelector('[data-zoom-hint]')!;
    const expanded = () => hint.getAttribute('data-expanded');

    (root.querySelector('[data-zoom-hint-open]') as HTMLButtonElement).click();
    fixture.detectChanges();
    hint.parentElement!.dispatchEvent(new MouseEvent('mouseenter'));
    tick(HINT_VISIBLE_MS * 2);
    fixture.detectChanges();
    expect(expanded()).toBe('true');

    (root.querySelector('[data-zoom-hint-close]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(expanded()).toBe('false');
  }));

  it('marca a lente aberta para esconder a dica, e o botão direito sobre a dica não abre a lente', () => {
    const { root, lens, diagram, center } = render();
    const hint = root.querySelector('[data-zoom-hint]')!;
    const area = hint.parentElement!;

    mouse(hint.querySelector('svg')!, 'mousedown', { button: 2, buttons: 2, ...center });
    expect(lens.isOpen).toBeFalse();

    mouse(diagram, 'mousedown', { button: 2, buttons: 2, ...center });
    expect(area.classList).toContain('lens-open');

    mouse(window, 'mouseup', { button: 2, buttons: 0, ...center });
    expect(area.classList).not.toContain('lens-open');
  });

  it('o botão esquerdo sozinho não abre a lente', () => {
    const { lens, diagram, center } = render();

    mouse(diagram, 'mousedown', { button: 0, buttons: 1, ...center });

    expect(lens.isOpen).toBeFalse();
  });

  it('a roda muda o aumento só com a lente aberta, e soltar volta ao aumento inicial', () => {
    const { lens, diagram, center, lensElement } = render();
    const wheel = (deltaY: number) => {
      const event = new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY, ...center });
      diagram.dispatchEvent(event);
      return event;
    };

    expect(wheel(-100).defaultPrevented).toBeFalse();

    mouse(diagram, 'mousedown', { button: 2, buttons: 2, ...center });
    expect(wheel(-100).defaultPrevented).toBeTrue();
    expect(lens.currentZoom).toBeCloseTo(INITIAL_ZOOM * ZOOM_FACTOR);

    const point = () => lensElement.querySelector('svg circle')!;
    expect(Number(point().getAttribute('r'))).toBeCloseTo(10 / (INITIAL_ZOOM * ZOOM_FACTOR));
    expect(Number(point().getAttribute('stroke-width'))).toBeCloseTo(2 / (INITIAL_ZOOM * ZOOM_FACTOR));

    for (let i = 0; i < 20; i++) wheel(-100);
    expect(lens.currentZoom).toBe(MAX_ZOOM);
    expect(Number(point().getAttribute('r'))).toBeCloseTo(10 / MAX_ZOOM);

    mouse(window, 'mouseup', { button: 2, buttons: 0, ...center });
    mouse(diagram, 'mousedown', { button: 2, buttons: 2, ...center });
    expect(lens.currentZoom).toBe(INITIAL_ZOOM);
    mouse(window, 'mouseup', { button: 2, buttons: 0, ...center });
  });

  it('no toque, abre só depois de segurar parado e fecha ao levantar o dedo', fakeAsync(() => {
    const { lens, diagram, center } = render();

    touch(diagram, 'touchstart', center);
    tick(LONG_PRESS_MS - 50);
    expect(lens.isOpen).toBeFalse();
    tick(50);
    expect(lens.isOpen).toBeTrue();

    expect(touch(diagram, 'touchmove', { clientX: center.clientX + 20, clientY: center.clientY }).defaultPrevented)
      .toBeTrue();

    touch(diagram, 'touchend', null);
    expect(lens.isOpen).toBeFalse();
    flush();
  }));

  it('arrastar o dedo antes do tempo é rolagem e não abre a lente', fakeAsync(() => {
    const { lens, diagram, center } = render();

    touch(diagram, 'touchstart', center);
    touch(diagram, 'touchmove', { clientX: center.clientX, clientY: center.clientY + 30 });
    tick(LONG_PRESS_MS * 2);

    expect(lens.isOpen).toBeFalse();
    flush();
  }));
});
