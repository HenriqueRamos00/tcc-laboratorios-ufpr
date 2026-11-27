import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type StatusPillTone = 'normal' | 'alerta' | 'nao-conforme' | 'neutro';

export const STATUS_PILL_SLOT_CLASS = 'inline-flex h-6 w-40 shrink-0 items-center';

export const STATUS_PILL_CLASS =
  'inline-flex h-6 w-40 shrink-0 items-center justify-center gap-1.5 whitespace-nowrap rounded-full px-2 text-xs font-semibold leading-none';

export const STATUS_PILL_TONE_CLASS: Record<StatusPillTone, string> = {
  normal: 'bg-lactec-tarja-normal text-lactec-on-tarja-normal',
  alerta: 'bg-lactec-tarja-alerta text-lactec-on-tarja-alerta',
  'nao-conforme': 'bg-lactec-tarja-nao-conforme text-lactec-on-tarja-nao-conforme',
  neutro: 'bg-lactec-muted text-white',
};

@Component({
  selector: 'app-status-pill',
  standalone: true,
  host: { class: 'inline-flex' },
  template: `<span [class]="pillClass + ' ' + toneClass[tone()]">{{ label() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusPillComponent {
  readonly label = input.required<string>();
  readonly tone = input<StatusPillTone>('neutro');

  protected readonly pillClass = STATUS_PILL_CLASS;
  protected readonly toneClass = STATUS_PILL_TONE_CLASS;
}

@Component({
  selector: 'app-status-pill-button',
  standalone: true,
  imports: [MatIconModule],
  host: { class: 'inline-flex' },
  template: `
    <button
      type="button"
      class="cursor-pointer border-0 hover:bg-lactec-ink-soft focus-visible:outline-2
             focus-visible:outline-offset-2 focus-visible:outline-lactec-primary
             disabled:cursor-progress disabled:hover:bg-lactec-muted"
      [class]="pillClass + ' ' + toneClass.neutro"
      [disabled]="loading()"
      [attr.aria-busy]="loading()"
      [attr.aria-label]="loading() ? null : (description() ?? null)"
      (click)="pressed.emit()"
    >
      <mat-icon class="icone-16 shrink-0 motion-reduce:animate-none" [class.animate-spin]="loading()" aria-hidden="true">sync</mat-icon>
      {{ loading() ? loadingLabel() : label() }}
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusPillButtonComponent {
  readonly label = input.required<string>();
  readonly loadingLabel = input.required<string>();
  readonly loading = input(false);
  readonly description = input<string>();

  readonly pressed = output<void>();

  protected readonly pillClass = STATUS_PILL_CLASS;
  protected readonly toneClass = STATUS_PILL_TONE_CLASS;
}
