import { Injectable } from '@angular/core';

export const SKELETON_DELAY_MS = 250;

export const SKELETON_HANDOFF_MS = 100;

@Injectable({ providedIn: 'root' })
export class SkeletonClock {
  private waiting = 0;
  private startedAt = 0;
  private endedAt = Number.NEGATIVE_INFINITY;

  enter(): number {
    const now = performance.now();
    if (this.waiting === 0 && now - this.endedAt > SKELETON_HANDOFF_MS) this.startedAt = now;
    this.waiting += 1;
    return SKELETON_DELAY_MS - (now - this.startedAt);
  }

  leave(): void {
    this.waiting -= 1;
    if (this.waiting === 0) this.endedAt = performance.now();
  }
}
