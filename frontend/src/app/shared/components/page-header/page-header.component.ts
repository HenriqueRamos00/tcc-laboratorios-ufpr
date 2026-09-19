import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

export interface MigalhaDePao {
  readonly rotulo: string;
  readonly rota?: string;
}

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [MatIconModule, RouterLink],
  host: { class: 'block' },
  template: `
    @if (migalhas().length) {
      <nav class="flex items-center gap-1 text-sm mb-2" aria-label="Trilha de navegação">
        @for (migalha of migalhas(); track migalha.rotulo; let last = $last) {
          @if (migalha.rota && !last) {
            <a [routerLink]="migalha.rota" class="text-lactec-primary hover:underline">
              {{ migalha.rotulo }}
            </a>
          } @else {
            <span class="text-lactec-ink-soft" [attr.aria-current]="last ? 'page' : null">
              {{ migalha.rotulo }}
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
          {{ titulo() }}
        </h1>
        @if (subtitulo()) {
          <p class="text-lactec-muted text-sm mt-1 m-0">{{ subtitulo() }}</p>
        }
      </div>
      <ng-content select="[acoes]" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderComponent {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string>();
  readonly migalhas = input<readonly MigalhaDePao[]>([]);
}
