import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <span class="inline-flex items-center gap-2 font-display text-2xl font-semibold tracking-tight">
      <img src="assets/lactec-logo.png" alt="" width="28" height="28" class="brightness-0 invert" />
      Lactec
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoComponent {}
