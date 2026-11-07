import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';

export interface StatusProgressStep {
  key: string;
  label: string;
  icon: string;
}

type StepState = 'completed' | 'active' | 'pending';

interface VisibleStep extends StatusProgressStep {
  state: StepState;
}

const HANDSET_QUERY = '(max-width: 767.98px)';
const DESKTOP_WINDOW_SIZE = 5;
const HANDSET_WINDOW_SIZE = 3;

@Component({
  selector: 'app-status-progress',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './status-progress.component.html',
  styleUrl: './status-progress.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusProgressComponent {
  private readonly breakpoints = inject(BreakpointObserver);

  readonly steps = input.required<readonly StatusProgressStep[]>();
  readonly currentIndex = input.required<number | null>();
  readonly label = input('Progresso do processo');

  private readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly visibleSteps = computed<readonly VisibleStep[]>(() => {
    const allSteps = this.steps();
    const current = this.currentIndex();
    const windowSize = this.isHandset() ? HANDSET_WINDOW_SIZE : DESKTOP_WINDOW_SIZE;
    const total = allSteps.length;
    const visibleCount = Math.min(total, windowSize);

    if (total <= windowSize) {
      return allSteps.map((step, index) => ({
        ...step,
        state: this.stateFor(index, current),
      }));
    }

    const centerOffset = Math.floor(windowSize / 2);
    let start = current === null ? 0 : current - centerOffset;
    let end = start + visibleCount;

    if (start < 0) {
      start = 0;
      end = visibleCount;
    }
    if (end > total) {
      end = total;
      start = total - visibleCount;
    }

    return allSteps.slice(start, end).map((step, index) => ({
      ...step,
      state: this.stateFor(start + index, current),
    }));
  });

  private stateFor(index: number, current: number | null): StepState {
    if (current === null) return 'pending';
    if (index < current) return 'completed';
    if (index === current) return 'active';
    return 'pending';
  }
}
