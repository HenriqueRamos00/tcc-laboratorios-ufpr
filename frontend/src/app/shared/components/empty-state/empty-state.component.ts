import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [MatIconModule],
  host: { class: 'block' },
  template: `
    <div
      class="flex flex-col items-center justify-center gap-2 rounded-2xl border border-dashed
             border-lactec-line bg-lactec-paper px-6 py-10 text-center"
      [attr.role]="tom() === 'erro' ? 'alert' : 'status'"
    >
      <mat-icon
        class="icone-36"
        [class.text-lactec-danger]="tom() === 'erro'"
        [class.text-lactec-faint]="tom() !== 'erro'"
        aria-hidden="true"
      >
        {{ icone() }}
      </mat-icon>
      <p class="m-0 text-sm font-medium text-lactec-ink">{{ titulo() }}</p>
      @if (descricao()) {
        <p class="m-0 max-w-prose text-sm text-lactec-muted">{{ descricao() }}</p>
      }
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly titulo = input.required<string>();
  readonly descricao = input<string>();
  readonly icone = input('inbox');
  readonly tom = input<'neutro' | 'erro'>('neutro');
}
