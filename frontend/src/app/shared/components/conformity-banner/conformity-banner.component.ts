import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  type Classification,
  CLASSIFICATION_LABEL,
  NOT_ASSESSED_LABEL,
} from '@/app/model/health';

// A tarja que o laudo impresso traz dentro do cabeçalho RESULTADO: um bloco
// sólido com a palavra escrita por cima. É outra forma que a marca de
// classificação da linha (bolinha mais rótulo) e por isso é outro componente:
// a tarja fala do resultado inteiro, a marca fala de um ensaio só.
//
// O par fundo/texto anda junto porque é ele que carrega o contraste. Texto
// branco sobre `lactec-warning` dá 2,2:1 e reprova em qualquer critério; os
// pares abaixo ficam em 4,8:1 (normal), 7,6:1 (alerta) e 8,4:1 (não conforme)
// contra o fundo escolhido.
const BANNER_COLOR_PAIR: Record<Classification, string> = {
  normal: 'bg-lactec-tarja-normal text-lactec-on-tarja-normal',
  alerta: 'bg-lactec-tarja-alerta text-lactec-on-tarja-alerta',
  'nao-conforme': 'bg-lactec-tarja-nao-conforme text-lactec-on-tarja-nao-conforme',
};

// Laudo sem parecer mantém a tarja no lugar, em cinza: tirar o bloco encolheria o
// cabeçalho RESULTADO e moveria a coluna inteira de um laudo para o outro.
const NOT_ASSESSED_COLOR_PAIR = 'bg-lactec-line-soft text-lactec-ink-soft';

@Component({
  selector: 'app-conformity-banner',
  standalone: true,
  host: { class: 'block' },
  template: `
    <span
      class="display block w-full px-3 py-1.5 text-center text-sm font-semibold
             tracking-wide uppercase"
      [class]="colorPair()"
    >
      {{ label() }}
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConformityBannerComponent {
  readonly classification = input.required<Classification | null>();

  protected readonly colorPair = computed(() => {
    const classification = this.classification();
    return classification ? BANNER_COLOR_PAIR[classification] : NOT_ASSESSED_COLOR_PAIR;
  });

  protected readonly label = computed(() => {
    const classification = this.classification();
    return classification ? CLASSIFICATION_LABEL[classification] : NOT_ASSESSED_LABEL;
  });
}
