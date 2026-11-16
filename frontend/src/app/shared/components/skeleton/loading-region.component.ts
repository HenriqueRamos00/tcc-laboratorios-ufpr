import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { SkeletonPlaceholderDirective } from './skeleton-placeholder.directive';

@Component({
  selector: 'app-loading-region',
  standalone: true,
  imports: [SkeletonPlaceholderDirective],
  host: { class: 'block', 'aria-busy': 'true' },
  template: `<div [appSkeletonPlaceholder]="label()"><ng-content /></div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingRegionComponent {
  readonly label = input.required<string>();
}
