import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { type Classification, CLASSIFICATION_LABEL } from '@/app/model/health';
import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';

// Classificação do ensaio. A cor acompanha, mas quem carrega a informação é o
// texto: em impressão preto e branco ou para quem não distingue as matizes, o
// rótulo continua legível.
//
// O mapa guarda a classe utilitária inteira, não o tom: assim o Tailwind
// encontra cada nome escrito por extenso no fonte e emite a utilitária.
const BACKGROUND_CLASS: Record<Classification, string> = {
  normal: 'bg-lactec-success',
  alerta: 'bg-lactec-warning',
  'nao-conforme': 'bg-lactec-danger',
};

@Component({
  selector: 'app-classification-badge',
  standalone: true,
  imports: [DataUnavailableComponent],
  host: { class: 'inline-flex' },
  template: `
    @if (classification(); as current) {
      <span class="inline-flex items-center gap-1.5 text-sm font-medium text-lactec-ink">
        <span
          class="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
          [class]="backgroundClass[current]"
          aria-hidden="true"
        ></span>
        {{ label[current] }}
      </span>
    } @else {
      <app-data-unavailable subject="Classificação" />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClassificationBadgeComponent {
  readonly classification = input.required<Classification | null>();

  protected readonly backgroundClass = BACKGROUND_CLASS;
  protected readonly label = CLASSIFICATION_LABEL;
}
