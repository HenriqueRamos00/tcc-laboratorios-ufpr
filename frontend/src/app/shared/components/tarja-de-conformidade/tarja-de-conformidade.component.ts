import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { type Classificacao, ROTULO_DA_CLASSIFICACAO } from '@/app/model/health';

// A tarja que o laudo impresso traz dentro do cabeçalho RESULTADO: um bloco
// sólido com a palavra escrita por cima. É outra forma que a marca de
// classificação da linha (bolinha mais rótulo) e por isso é outro componente:
// a tarja fala do resultado inteiro, a marca fala de um ensaio só.
//
// O par fundo/texto anda junto porque é ele que carrega o contraste. Texto
// branco sobre `lactec-warning` dá 2,2:1 e reprova em qualquer critério; os
// pares abaixo ficam em 4,8:1 (normal), 7,6:1 (alerta) e 8,4:1 (não conforme)
// contra o fundo escolhido.
const PAR_DE_CORES_DA_TARJA: Record<Classificacao, string> = {
  normal: 'bg-lactec-tarja-normal text-lactec-on-tarja-normal',
  alerta: 'bg-lactec-tarja-alerta text-lactec-on-tarja-alerta',
  'nao-conforme': 'bg-lactec-tarja-nao-conforme text-lactec-on-tarja-nao-conforme',
};

@Component({
  selector: 'app-tarja-de-conformidade',
  standalone: true,
  host: { class: 'block' },
  template: `
    <span
      class="display block w-full px-3 py-1.5 text-center text-sm font-semibold
             tracking-wide uppercase"
      [class]="parDeCores()"
    >
      {{ rotulo() }}
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarjaDeConformidadeComponent {
  readonly classificacao = input.required<Classificacao>();

  protected readonly parDeCores = computed(() => PAR_DE_CORES_DA_TARJA[this.classificacao()]);
  protected readonly rotulo = computed(() => ROTULO_DA_CLASSIFICACAO[this.classificacao()]);
}
