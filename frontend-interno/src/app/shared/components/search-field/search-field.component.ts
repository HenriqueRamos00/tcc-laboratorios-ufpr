import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-search-field',
  standalone: true,
  imports: [MatIconModule],
  host: { class: 'block' },
  template: `
    <label class="input-field">
      <mat-icon class="input-field__icon" aria-hidden="true">search</mat-icon>
      <input
        type="search"
        class="input-field__input"
        [value]="value()"
        [placeholder]="placeholder()"
        [attr.aria-label]="label()"
        (input)="onInput($event)"
      />
    </label>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchFieldComponent {
  readonly value = input('');
  readonly placeholder = input('Pesquisar...');
  readonly label = input('Pesquisar');
  readonly valueChange = output<string>();

  onInput(event: Event): void {
    this.valueChange.emit((event.currentTarget as HTMLInputElement).value);
  }
}
