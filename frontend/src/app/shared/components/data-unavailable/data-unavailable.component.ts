import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export const DATA_UNAVAILABLE = 'Dados não disponíveis';

@Component({
  selector: 'app-data-unavailable',
  standalone: true,
  host: { class: 'inline' },
  template: `<span class="data-unavailable text-sm font-normal italic normal-case tracking-normal text-lactec-muted">@if (subject()) {<span class="sr-only">{{ subject() }}: </span>}{{ text }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataUnavailableComponent {
  readonly subject = input<string>();

  protected readonly text = DATA_UNAVAILABLE;
}

export function isUnavailableText(value: string | null | undefined): boolean {
  const text = value?.trim() ?? '';
  return text === '' || text === '-';
}
