import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';

export interface StatusStepperStep {
  key: string;
  label: string;
  icon?: string;
}

type StepState = 'completed' | 'active' | 'pending';

interface VisibleStep extends StatusStepperStep {
  absoluteIndex: number;
  state: StepState;
}

const HANDSET_QUERY = '(max-width: 767.98px)';

@Component({
  selector: 'app-status-stepper',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './status-stepper.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusStepperComponent {
  private readonly breakpoints = inject(BreakpointObserver);

  readonly steps = input.required<readonly StatusStepperStep[]>();
  readonly currentIndex = input.required<number>();

  private readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  readonly visibleSteps = computed<readonly VisibleStep[]>(() => {
    const allSteps = this.steps();
    const current = this.currentIndex();
    const windowSize = this.isHandset() ? 3 : 5;
    const total = allSteps.length;

    if (total <= windowSize) {
      return allSteps.map((step, i) => ({
        ...step,
        absoluteIndex: i,
        state: this.stateFor(i, current),
      }));
    }

    const half = Math.floor(windowSize / 2);
    let start = current - half;
    let end = start + windowSize;

    if (start < 0) {
      start = 0;
      end = windowSize;
    }
    if (end > total) {
      end = total;
      start = total - windowSize;
    }

    return allSteps.slice(start, end).map((step, i) => ({
      ...step,
      absoluteIndex: start + i,
      state: this.stateFor(start + i, current),
    }));
  });

  private stateFor(index: number, current: number): StepState {
    if (index < current) return 'completed';
    if (index === current) return 'active';
    return 'pending';
  }
}
