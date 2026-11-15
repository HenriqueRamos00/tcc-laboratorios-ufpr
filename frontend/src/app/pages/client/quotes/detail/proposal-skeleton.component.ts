import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { SkeletonComponent } from '@shared/components/skeleton/skeleton.component';
import { skeletonIndexes } from '@shared/components/skeleton/table-skeleton.component';

const PARAGRAPH_COUNT = 4;

@Component({
  selector: 'app-proposal-skeleton',
  standalone: true,
  imports: [SkeletonComponent],
  host: { class: 'block' },
  template: `
    <div
      class="overflow-hidden bg-lactec-paper"
      [class.rounded-xl]="framed()"
      [class.border]="framed()"
      [class.border-lactec-line]="framed()"
    >
      @if (framed()) {
        <div class="flex items-center justify-between gap-2 border-b border-lactec-line px-3 py-2">
          <app-skeleton class="h-4 w-36" />
          <app-skeleton class="h-9 w-40" shape="block" />
        </div>
      }

      <div
        class="flex justify-center overflow-hidden bg-lactec-surface px-4 pt-6"
        [style.height]="height()"
      >
        <div class="flex h-full w-full max-w-3xl flex-col gap-3 bg-lactec-paper p-8 shadow-sm">
          <app-skeleton class="h-10 w-40" shape="block" />
          <app-skeleton class="mt-4 h-6 w-2/3" />
          <app-skeleton class="h-4 w-1/3" />
          @for (paragraph of paragraphs; track paragraph) {
            <div class="mt-4 flex flex-col gap-2">
              <app-skeleton class="h-3.5 w-full" />
              <app-skeleton class="h-3.5 w-full" />
              <app-skeleton class="h-3.5 w-11/12" />
              <app-skeleton class="h-3.5 w-3/5" />
            </div>
          }
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProposalSkeletonComponent {
  readonly framed = input(true);
  readonly height = input('70vh');

  protected readonly paragraphs = skeletonIndexes(PARAGRAPH_COUNT);
}
