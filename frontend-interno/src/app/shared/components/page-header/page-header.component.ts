import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

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
      <nav class="mb-2 flex items-center gap-1 text-sm" aria-label="Trilha de navegação">
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
            <mat-icon class="icone-16 text-lactec-muted" aria-hidden="true">
              chevron_right
            </mat-icon>
          }
        }
      </nav>
    }

    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="min-w-0">
        <h1 class="display m-0 text-2xl font-semibold text-lactec-ink md:text-3xl">
          {{ title() }}
        </h1>
        @if (subtitle()) {
          <p class="mt-1 mb-0 text-sm text-lactec-muted">{{ subtitle() }}</p>
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
