import { DOCUMENT } from '@angular/common';
import { afterNextRender, DestroyRef, inject, signal, type Signal } from '@angular/core';

import { MAX_PAGE_SIZE } from '@/app/model/page';

export const SCROLL_AREA_ATTRIBUTE = 'data-area-rolavel';

export const RESIZE_DEBOUNCE_MS = 80;

const ROWS_BEFORE_MEASURING = 3;

export interface ViewportRowsConfig {
  readonly host: () => HTMLElement | undefined;
  readonly reservedHeight: () => number;
  readonly rowHeight: () => number;
  readonly minimum?: () => number;
  readonly maximum?: number;
}

export function viewportRows(config: ViewportRowsConfig): Signal<number> {
  const { host, reservedHeight, rowHeight, minimum = () => 1, maximum = MAX_PAGE_SIZE } = config;

  const rows = signal(Math.max(minimum(), ROWS_BEFORE_MEASURING));
  const view = inject(DOCUMENT).defaultView;
  const destroyRef = inject(DestroyRef);

  const measure = (): void => {
    const element = host();
    const height = rowHeight();
    const scrollArea = element?.closest<HTMLElement>(`[${SCROLL_AREA_ATTRIBUTE}]`);

    if (!element || !scrollArea || !view || height <= 0 || scrollArea.clientHeight === 0) return;

    const above = Math.max(
      0,
      element.getBoundingClientRect().top -
        scrollArea.getBoundingClientRect().top +
        scrollArea.scrollTop,
    );
    const paddingBelow = parseFloat(view.getComputedStyle(scrollArea).paddingBottom) || 0;
    const available = scrollArea.clientHeight - above - paddingBelow - reservedHeight();

    rows.set(Math.min(maximum, Math.max(minimum(), Math.floor(available / height))));
  };

  let pending: ReturnType<typeof setTimeout> | undefined;
  const scheduleMeasure = (): void => {
    clearTimeout(pending);
    pending = setTimeout(measure, RESIZE_DEBOUNCE_MS);
  };

  afterNextRender({ read: measure });

  view?.addEventListener('resize', scheduleMeasure);

  destroyRef.onDestroy(() => {
    clearTimeout(pending);
    view?.removeEventListener('resize', scheduleMeasure);
  });

  return rows.asReadonly();
}
