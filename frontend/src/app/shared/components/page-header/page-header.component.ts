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
      <nav class="mb-2 text-sm" aria-label="Trilha de navegação">
        <ol class="m-0 flex list-none flex-wrap items-center gap-x-1 gap-y-1 p-0">
          @for (breadcrumb of breadcrumbs(); track breadcrumb.label; let last = $last) {
            <li class="inline-flex min-w-0 items-center gap-1">
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
                <mat-icon class="icone-16 shrink-0 text-lactec-faint" aria-hidden="true">
                  chevron_right
                </mat-icon>
              }
            </li>
          }
        </ol>
      </nav>
    }

    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="min-w-0">
        <h1 class="font-display tracking-display text-2xl md:text-3xl font-semibold text-lactec-ink m-0">
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
