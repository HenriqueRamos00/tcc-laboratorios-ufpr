import { flushMicrotasks, type ComponentFixture } from '@angular/core/testing';

export const PAGE_SIZE_WITHOUT_SCROLL_AREA = 3;

export function renderMeasuredTable(fixture: ComponentFixture<unknown>): void {
  fixture.detectChanges();
  flushMicrotasks();
  fixture.detectChanges();
  fixture.detectChanges();
}
