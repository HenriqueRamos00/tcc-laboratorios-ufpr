import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  type Classification,
  CLASSIFICATION_LABEL,
  NOT_ASSESSED_LABEL,
} from '@/app/model/health';

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

// Ensaio sem parecer fica cinza: a bolinha continua na linha para a coluna não
// desalinhar, e o tom neutro não promete faixa nenhuma.
const NOT_ASSESSED_BACKGROUND_CLASS = 'bg-lactec-muted';

@Component({
  selector: 'app-classification-badge',
  standalone: true,
  host: { class: 'inline-flex' },
  template: `
    <span class="inline-flex items-center gap-1.5 text-sm font-medium text-lactec-ink">
      <span
        class="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
        [class]="backgroundClass()"
        aria-hidden="true"
      ></span>
      {{ label() }}
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClassificationBadgeComponent {
  readonly classification = input.required<Classification | null>();

  protected readonly backgroundClass = computed(() => {
    const classification = this.classification();
    return classification ? BACKGROUND_CLASS[classification] : NOT_ASSESSED_BACKGROUND_CLASS;
  });

  protected readonly label = computed(() => {
    const classification = this.classification();
    return classification ? CLASSIFICATION_LABEL[classification] : NOT_ASSESSED_LABEL;
  });
}
