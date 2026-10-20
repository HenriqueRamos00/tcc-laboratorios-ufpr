import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <span class="inline-flex items-center gap-3">
      <img
        src="assets/lactec-logo.png"
        alt="Lactec"
        class="inline-block object-contain"
        [style.width.px]="imgSize()"
        [style.height.px]="imgSize()"
        [style.filter]="filter()"
      />
      @if (showWordmark()) {
        <span
          class="font-display font-semibold tracking-tight"
          [style.font-size.px]="size()"
          [style.color]="wordmarkColor()"
        >Lactec</span>
      }
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoComponent {
  readonly size = input(22);
  readonly iconSize = input<number | undefined>(undefined);
  readonly variant = input<'light' | 'dark'>('light');
  readonly showWordmark = input(true);

  readonly imgSize = computed(() => this.iconSize() ?? Math.round(this.size() * 1.15));
  readonly wordmarkColor = computed(() => (this.variant() === 'light' ? '#FFFFFF' : '#0F4C5C'));
  readonly filter = computed(() =>
    this.variant() === 'light'
      ? 'brightness(0) invert(1)'
      : 'invert(23%) sepia(23%) saturate(2200%) hue-rotate(158deg) brightness(92%) contrast(92%)',
  );
}
