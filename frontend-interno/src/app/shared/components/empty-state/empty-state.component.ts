import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [MatIconModule],
  host: { class: 'block' },
  template: `
    <section
      class="flex min-h-52 flex-col items-center justify-center gap-3 rounded-2xl border
             border-lactec-line bg-lactec-paper px-6 py-8 text-center"
      [class.border-dashed]="tom() === 'neutro'"
      [attr.role]="tom() === 'erro' ? 'alert' : 'status'"
    >
      <mat-icon
        class="icone-36"
        [class.text-lactec-danger]="tom() === 'erro'"
        [class.text-lactec-muted]="tom() !== 'erro'"
        aria-hidden="true"
      >
        {{ icon() }}
      </mat-icon>
      <div>
        <h2 class="m-0 text-base font-semibold text-lactec-ink">{{ title() }}</h2>
        @if (descricao()) {
          <p class="mt-1 mb-0 text-sm text-lactec-muted">{{ descricao() }}</p>
        }
      </div>
      <ng-content />
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly descricao = input<string>();
  readonly icon = input('inbox');
  readonly tom = input<'neutro' | 'erro'>('neutro');
}
