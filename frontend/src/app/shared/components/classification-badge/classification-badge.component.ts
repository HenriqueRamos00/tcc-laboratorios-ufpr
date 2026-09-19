import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { type Classificacao, ROTULO_DA_CLASSIFICACAO } from '@/app/model/health';

// Classificação do ensaio. A cor acompanha, mas quem carrega a informação é o
// texto: em impressão preto e branco ou para quem não distingue as matizes, o
// rótulo continua legível.
const COR: Record<Classificacao, string> = {
  normal: '#1baf7a',
  alerta: '#eda100',
  'nao-conforme': '#e34948',
};

@Component({
  selector: 'app-classification-badge',
  standalone: true,
  host: { class: 'inline-flex' },
  template: `
    <span class="inline-flex items-center gap-1.5 text-sm font-medium text-lactec-ink">
      <span
        class="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
        [style.background]="cor()"
        aria-hidden="true"
      ></span>
      {{ rotulo() }}
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClassificationBadgeComponent {
  readonly classificacao = input.required<Classificacao>();

  protected readonly cor = computed(() => COR[this.classificacao()]);
  protected readonly rotulo = computed(() => ROTULO_DA_CLASSIFICACAO[this.classificacao()]);
}
