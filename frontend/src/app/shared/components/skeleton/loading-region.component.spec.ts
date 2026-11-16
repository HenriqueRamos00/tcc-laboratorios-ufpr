import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Component, signal } from '@angular/core';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';

import { LoadingRegionComponent } from './loading-region.component';
import {
  SKELETON_DELAY_MS,
  SKELETON_HANDOFF_MS,
  SkeletonClock,
} from './skeleton-clock.service';
import { SkeletonComponent } from './skeleton.component';

@Component({
  standalone: true,
  imports: [LoadingRegionComponent, SkeletonComponent],
  template: `
    @if (stage() === 'primeira') {
      <app-loading-region id="primeira" label="Carregando equipamento…">
        <app-skeleton class="h-4 w-32" />
        <app-skeleton class="h-11 w-11" shape="round" />
      </app-loading-region>
    } @else if (stage() === 'segunda') {
      <app-loading-region id="segunda" label="Carregando histórico…">
        <app-skeleton class="h-4 w-32" />
      </app-loading-region>
    } @else {
      <p>Transformador TR-00001</p>
    }
  `,
})
class RegionHostComponent {
  readonly stage = signal<'primeira' | 'segunda' | 'pronto'>('primeira');
}

function mount() {
  const announce = spyOn(TestBed.inject(LiveAnnouncer), 'announce').and.resolveTo();
  const fixture = TestBed.createComponent(RegionHostComponent);
  fixture.detectChanges();
  return { fixture, announce, element: fixture.nativeElement as HTMLElement };
}

function placeholder(element: HTMLElement): HTMLElement {
  return element.querySelector<HTMLElement>('app-loading-region > div')!;
}

function revealDelay(element: HTMLElement): number {
  return parseFloat(placeholder(element).style.getPropertyValue('--skeleton-delay'));
}

describe('LoadingRegionComponent', () => {
  it('marca a região como ocupada e esconde as barras do leitor de tela', () => {
    const { element } = mount();

    expect(element.querySelector('app-loading-region')?.getAttribute('aria-busy')).toBe('true');
    expect(placeholder(element).getAttribute('aria-hidden')).toBe('true');
    expect(placeholder(element).querySelectorAll('app-skeleton').length).toBe(2);
    expect(element.querySelector('[role="status"]')).toBeNull();
    expect(element.textContent?.trim()).toBe('');
  });

  it('segura a entrada das barras pelo tempo de espera, com as barras já no lugar', () => {
    const { element } = mount();
    const style = getComputedStyle(placeholder(element));

    expect(revealDelay(element)).toBeGreaterThan(SKELETON_DELAY_MS - 50);
    expect(revealDelay(element)).toBeLessThanOrEqual(SKELETON_DELAY_MS);
    expect(style.animationName).toBe('skeleton-reveal');
    expect(style.animationDelay).toBe(`${revealDelay(element) / 1000}s`);
    expect(style.animationFillMode).toBe('both');
    expect(style.display).not.toBe('none');
  });

  it('anuncia o rótulo uma vez, depois da espera', fakeAsync(() => {
    const { announce } = mount();

    tick(SKELETON_DELAY_MS - 10);
    expect(announce).not.toHaveBeenCalled();

    tick(SKELETON_DELAY_MS);
    expect(announce).toHaveBeenCalledOnceWith('Carregando equipamento…', 'polite');
  }));

  it('não anuncia quando o conteúdo chega antes da espera', fakeAsync(() => {
    const { fixture, announce, element } = mount();

    fixture.componentInstance.stage.set('pronto');
    fixture.detectChanges();
    tick(SKELETON_DELAY_MS * 2);

    expect(announce).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Transformador TR-00001');
  }));

  it('as barras pulsam só para quem não pediu menos movimento', () => {
    const { element } = mount();
    const bar = element.querySelector('app-skeleton')!;

    expect(bar.classList).toContain('motion-safe:animate-pulse');
    expect(bar.classList).not.toContain('animate-pulse');
  });

  it('a barra assume a forma pedida', () => {
    const { element } = mount();
    const [line, round] = Array.from(element.querySelectorAll('app-skeleton'));

    expect(line.classList).toContain('rounded-md');
    expect(round.classList).toContain('rounded-full');
    expect(round.classList).toContain('bg-lactec-line');
  });
});

describe('SkeletonClock', () => {
  let now = 0;

  beforeEach(() => {
    now = 1_000;
    spyOn(performance, 'now').and.callFake(() => now);
  });

  it('a primeira espera começa do zero', () => {
    expect(TestBed.inject(SkeletonClock).enter()).toBe(SKELETON_DELAY_MS);
  });

  it('a espera que emenda na anterior herda o tempo já esperado', () => {
    const clock = TestBed.inject(SkeletonClock);

    clock.enter();
    now += 900;
    clock.leave();
    now += SKELETON_HANDOFF_MS;

    expect(clock.enter()).toBe(SKELETON_DELAY_MS - 900 - SKELETON_HANDOFF_MS);
  });

  it('a espera que começa depois de a tela assentar volta a contar do zero', () => {
    const clock = TestBed.inject(SkeletonClock);

    clock.enter();
    now += 900;
    clock.leave();
    now += SKELETON_HANDOFF_MS + 1;

    expect(clock.enter()).toBe(SKELETON_DELAY_MS);
  });

  it('a região que troca de lugar com outra já visível entra sem nova espera e anuncia na hora', () => {
    const announce = spyOn(TestBed.inject(LiveAnnouncer), 'announce').and.resolveTo();
    const fixture = TestBed.createComponent(RegionHostComponent);
    fixture.detectChanges();

    now += 900;
    fixture.componentInstance.stage.set('segunda');
    fixture.detectChanges();

    expect(revealDelay(fixture.nativeElement)).toBe(SKELETON_DELAY_MS - 900);
    expect(announce).toHaveBeenCalledOnceWith('Carregando histórico…', 'polite');
  });
});
