import { LiveAnnouncer } from '@angular/cdk/a11y';
import { DestroyRef, Directive, inject, input, NgZone, type OnInit } from '@angular/core';

import { SkeletonClock } from './skeleton-clock.service';

@Directive({
  selector: '[appSkeletonPlaceholder]',
  standalone: true,
  host: {
    class: 'skeleton-reveal',
    'aria-hidden': 'true',
    '[style.--skeleton-delay]': 'revealDelay',
  },
})
export class SkeletonPlaceholderDirective implements OnInit {
  readonly label = input.required<string>({ alias: 'appSkeletonPlaceholder' });

  private readonly announcer = inject(LiveAnnouncer);
  private readonly zone = inject(NgZone);
  private readonly clock = inject(SkeletonClock);
  private readonly destroyRef = inject(DestroyRef);

  private readonly remaining = Math.round(this.clock.enter());

  protected readonly revealDelay = `${this.remaining}ms`;

  constructor() {
    this.destroyRef.onDestroy(() => this.clock.leave());
  }

  ngOnInit(): void {
    const announce = (): void => void this.announcer.announce(this.label(), 'polite');

    if (this.remaining <= 0) {
      announce();
      return;
    }

    const pending = this.zone.runOutsideAngular(() => setTimeout(announce, this.remaining));
    this.destroyRef.onDestroy(() => clearTimeout(pending));
  }
}
