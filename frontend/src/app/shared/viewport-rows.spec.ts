import { Component, ElementRef, signal, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { MAX_PAGE_SIZE } from '@/app/model/page';

import { RESIZE_DEBOUNCE_MS, viewportRows } from './viewport-rows';

const ROW_HEIGHT = 50;
const RESERVED_HEIGHT = 80;
const PADDING_BELOW = 20;

@Component({
  standalone: true,
  template: `
    <div
      data-area-rolavel
      style="overflow: auto; box-sizing: border-box; padding: 0"
      [style.height.px]="areaHeight()"
      [style.padding-bottom.px]="paddingBelow"
    >
      <div [style.height.px]="aboveHeight()"></div>
      <div #host [style.height.px]="contentHeight()"></div>
    </div>
  `,
})
class MeasuredHostComponent {
  readonly areaHeight = signal(600);
  readonly aboveHeight = signal(100);
  readonly contentHeight = signal(10);
  readonly minimum = signal(1);
  readonly paddingBelow = PADDING_BELOW;

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');

  readonly rows = viewportRows({
    host: () => this.host().nativeElement,
    reservedHeight: () => RESERVED_HEIGHT,
    rowHeight: () => ROW_HEIGHT,
    minimum: () => this.minimum(),
  });
}

@Component({
  standalone: true,
  template: `<div #host style="height: 900px"></div>`,
})
class HostWithoutScrollAreaComponent {
  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');

  readonly rows = viewportRows({
    host: () => this.host().nativeElement,
    reservedHeight: () => RESERVED_HEIGHT,
    rowHeight: () => ROW_HEIGHT,
  });
}

function waitForTheResizeDebounce(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, RESIZE_DEBOUNCE_MS + 40));
}

describe('viewportRows, as linhas que cabem na área rolável', () => {
  it('antes de medir, e fora de uma área rolável, vale três linhas', () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);

    expect(fixture.componentInstance.rows()).toBe(3);

    TestBed.resetTestingModule();
    const semArea = TestBed.createComponent(HostWithoutScrollAreaComponent);
    semArea.detectChanges();

    expect(semArea.componentInstance.rows()).toBe(3);
  });

  it('desconta o que está acima, o padding de baixo e a altura reservada, e divide pela linha', () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.rows()).toBe(
      Math.floor((600 - 100 - PADDING_BELOW - RESERVED_HEIGHT) / ROW_HEIGHT),
    );
  });

  it('não muda quando só o conteúdo muda, e remede quando a janela muda', async () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);
    fixture.detectChanges();
    const medido = fixture.componentInstance.rows();

    fixture.componentInstance.contentHeight.set(2000);
    fixture.componentInstance.areaHeight.set(900);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.rows()).toBe(medido);

    window.dispatchEvent(new Event('resize'));

    expect(fixture.componentInstance.rows()).toBe(medido);

    await waitForTheResizeDebounce();

    expect(fixture.componentInstance.rows()).toBe(
      Math.floor((900 - 100 - PADDING_BELOW - RESERVED_HEIGHT) / ROW_HEIGHT),
    );
  });

  it('a rolagem da área não entra na conta do que está acima', async () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);
    fixture.componentInstance.contentHeight.set(2000);
    fixture.detectChanges();
    const medido = fixture.componentInstance.rows();

    const area = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '[data-area-rolavel]',
    )!;
    area.scrollTop = 300;
    window.dispatchEvent(new Event('resize'));
    await waitForTheResizeDebounce();

    expect(area.scrollTop).toBe(300);
    expect(fixture.componentInstance.rows()).toBe(medido);
  });

  it('nunca desce do mínimo, mesmo sem espaço nenhum', () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);
    fixture.componentInstance.aboveHeight.set(5000);
    fixture.detectChanges();

    expect(fixture.componentInstance.rows()).toBe(1);

    TestBed.resetTestingModule();
    const comPiso = TestBed.createComponent(MeasuredHostComponent);
    comPiso.componentInstance.aboveHeight.set(5000);
    comPiso.componentInstance.minimum.set(5);
    comPiso.detectChanges();

    expect(comPiso.componentInstance.rows()).toBe(5);
  });

  it('nunca passa do limite de página da API', () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);
    fixture.componentInstance.areaHeight.set(100_000);
    fixture.detectChanges();

    expect(fixture.componentInstance.rows()).toBe(MAX_PAGE_SIZE);
  });

  it('para de ouvir a janela quando o componente é destruído', async () => {
    const fixture = TestBed.createComponent(MeasuredHostComponent);
    fixture.detectChanges();
    const medido = fixture.componentInstance.rows();

    fixture.componentInstance.areaHeight.set(900);
    fixture.detectChanges();
    window.dispatchEvent(new Event('resize'));
    fixture.destroy();
    await waitForTheResizeDebounce();

    expect(fixture.componentInstance.rows()).toBe(medido);
  });
});
