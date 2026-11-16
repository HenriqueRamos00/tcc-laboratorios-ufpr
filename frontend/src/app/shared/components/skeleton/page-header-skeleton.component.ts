import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { SkeletonComponent } from './skeleton.component';

@Component({
  selector: 'app-page-header-skeleton',
  standalone: true,
  imports: [SkeletonComponent],
  host: { class: 'block' },
  template: `
    @if (breadcrumbs()) {
      <div class="mb-2 flex h-5 items-center gap-2">
        <app-skeleton class="h-3.5 w-40" />
        <app-skeleton class="h-3.5 w-24" />
      </div>
    }

    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="min-w-0">
        <div class="flex h-8 items-center md:h-9">
          <app-skeleton class="h-6 w-72 max-w-full md:h-7" />
        </div>
        @if (subtitle()) {
          <div class="mt-1 flex h-5 items-center">
            <app-skeleton class="h-3.5 w-56 max-w-full" />
          </div>
        }
      </div>
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderSkeletonComponent {
  readonly breadcrumbs = input(false);
  readonly subtitle = input(false);
}
