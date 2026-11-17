import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { SkeletonComponent } from './skeleton.component';
import { skeletonIndexes } from './table-skeleton.component';

const FIELDS_AFTER_THE_ID = skeletonIndexes(4);

@Component({
  selector: 'app-quote-card-skeleton',
  standalone: true,
  imports: [SkeletonComponent],
  host: { class: 'block' },
  template: `
    <div class="flex flex-col gap-4 rounded-[12px] border border-lactec-line bg-lactec-paper p-5 shadow-lactec-raised md:p-6">
      <div class="flex flex-col gap-4 md:grid md:grid-cols-[1fr_1fr_auto] md:grid-rows-[auto_auto_1fr] md:gap-x-10">
        <div class="flex items-start justify-between gap-3 md:contents">
          <div class="flex min-w-0 flex-col gap-1 md:col-start-1 md:row-start-1">
            <app-skeleton class="my-0.5 h-3.5 w-20" />
            <app-skeleton class="my-0.5 h-4 w-28" />
          </div>
          <app-skeleton class="h-6 w-32 md:col-start-3 md:row-start-1 md:justify-self-end" />
        </div>

        <div class="flex flex-col gap-4 md:contents">
          @for (field of fields; track field) {
            <div class="flex min-w-0 flex-col gap-1">
              <app-skeleton class="my-0.5 h-3.5 w-24" />
              <app-skeleton class="my-0.5 h-4 w-40 max-w-full" />
            </div>
          }
        </div>

        <div class="flex flex-col gap-2 md:col-start-3 md:row-[2/-1] md:min-w-48 md:self-end">
          @for (action of actionIndexes(); track action) {
            <app-skeleton class="h-10 w-full" shape="block" />
          }
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteCardSkeletonComponent {
  readonly actions = input(1);

  protected readonly fields = FIELDS_AFTER_THE_ID;
  protected readonly actionIndexes = computed(() => skeletonIndexes(this.actions()));
}
