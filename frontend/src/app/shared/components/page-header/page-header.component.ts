import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

/** Um degrau da trilha de navegação: o rótulo que o cliente lê e para onde ele leva. */
export interface Breadcrumb {
  readonly label: string;
  readonly route?: string;
}

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [MatIconModule, RouterLink],
  host: { class: 'block' },
  template: `
    @if (breadcrumbs().length) {
      <nav class="flex items-center gap-1 text-sm mb-2" aria-label="Trilha de navegação">
        @for (breadcrumb of breadcrumbs(); track breadcrumb.label; let last = $last) {
          @if (breadcrumb.route && !last) {
            <a [routerLink]="breadcrumb.route" class="text-lactec-primary hover:underline">
              {{ breadcrumb.label }}
            </a>
          } @else {
            <span class="text-lactec-ink-soft" [attr.aria-current]="last ? 'page' : null">
              {{ breadcrumb.label }}
            </span>
          }
          @if (!last) {
            <mat-icon class="icone-16 text-lactec-faint" aria-hidden="true">
              chevron_right
            </mat-icon>
          }
        }
      </nav>
    }

    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="min-w-0">
        <h1 class="display text-2xl md:text-3xl font-semibold text-lactec-ink m-0">
          {{ title() }}
        </h1>
        @if (subtitle()) {
          <p class="text-lactec-muted text-sm mt-1 m-0">{{ subtitle() }}</p>
        }
      </div>
      <ng-content select="[acoes]" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly breadcrumbs = input<readonly Breadcrumb[]>([]);
}
