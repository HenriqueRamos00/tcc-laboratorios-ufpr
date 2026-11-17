import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-search-field',
  standalone: true,
  imports: [FormsModule, MatIconModule],
  host: { class: 'block' },
  template: `
    <label class="relative flex items-center">
      <mat-icon
        class="icone-20 pointer-events-none absolute left-3 text-lactec-muted"
        aria-hidden="true"
      >
        search
      </mat-icon>
      <input
        type="search"
        class="h-12 w-full rounded-lg border border-lactec-field-border bg-lactec-paper pl-11 pr-3 text-sm
               text-lactec-ink placeholder:text-lactec-muted
               hover:border-lactec-field-border-hover focus:border-lactec-field-border-focus focus:outline-none focus:ring-1 focus:ring-lactec-field-border-focus"
        [attr.aria-label]="label()"
        [placeholder]="placeholder()"
        [attr.maxlength]="maxLength()"
        [ngModel]="termo()"
        (ngModelChange)="termo.set($event)"
      />
    </label>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchFieldComponent {
  readonly termo = model('');
  readonly placeholder = input('Pesquisar...');
  readonly label = input('Pesquisar');
  readonly maxLength = input<number | null>(null);
}
