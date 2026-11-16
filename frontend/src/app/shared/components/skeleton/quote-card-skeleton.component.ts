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
    <div class="quote-card">
      <div class="quote-card-body">
        <div class="quote-card-body__header">
          <div class="quote-card-field">
            <app-skeleton class="my-0.5 h-3.5 w-20" />
            <app-skeleton class="my-0.5 h-4 w-28" />
          </div>
          <app-skeleton class="quote-status-pill h-6 w-32" />
        </div>

        <div class="quote-card-body__content">
          @for (field of fields; track field) {
            <div class="quote-card-field">
              <app-skeleton class="my-0.5 h-3.5 w-24" />
              <app-skeleton class="my-0.5 h-4 w-40 max-w-full" />
            </div>
          }
        </div>

        <div class="quote-card-body__footer">
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
