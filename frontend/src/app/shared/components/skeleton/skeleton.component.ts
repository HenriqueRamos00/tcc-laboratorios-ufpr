import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type SkeletonShape = 'line' | 'block' | 'round';
export type SkeletonTone = 'soft' | 'strong';

const SHAPE_CLASS: Record<SkeletonShape, string> = {
  line: 'rounded-md',
  block: 'rounded-xl',
  round: 'rounded-full',
};

const TONE_CLASS: Record<SkeletonTone, string> = {
  soft: 'bg-lactec-line',
  strong: 'bg-lactec-line-strong',
};

@Component({
  selector: 'app-skeleton',
  standalone: true,
  template: '',
  host: {
    class: 'block motion-safe:animate-pulse',
    '[class]': 'appearance()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonComponent {
  readonly shape = input<SkeletonShape>('line');
  readonly tone = input<SkeletonTone>('soft');

  protected readonly appearance = computed(
    () => `${SHAPE_CLASS[this.shape()]} ${TONE_CLASS[this.tone()]}`,
  );
}
