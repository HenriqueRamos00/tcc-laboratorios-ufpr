import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { type Classificacao, FAIXAS_DE_CONFORMIDADE } from '@/app/model/health';

// É um resumo do laudo e não uma medição contínua: por isso o ponteiro aponta
// para o meio da faixa, e o veredito aparece escrito abaixo, porque cor sozinha
// nunca carrega a informação.
const RAIO = 62;
const CENTRO_X = 80;
const CENTRO_Y = 74;

const COR_DA_FAIXA: Record<Classificacao, string> = {
  normal: '#1baf7a',
  alerta: '#eda100',
  'nao-conforme': '#e34948',
};

interface Arco {
  readonly chave: Classificacao;
  readonly caminho: string;
  readonly cor: string;
}

// 180° é a ponta esquerda do arco e 0° a direita, com o y invertido porque no
// SVG ele cresce para baixo.
function pontoNoArco(anguloEmGraus: number, raio: number): [number, number] {
  const radianos = (Math.PI * anguloEmGraus) / 180;
  return [CENTRO_X + raio * Math.cos(radianos), CENTRO_Y - raio * Math.sin(radianos)];
}

@Component({
  selector: 'app-conformity-gauge',
  standalone: true,
  host: { class: 'block' },
  template: `
    <figure class="m-0 flex flex-col items-center gap-3">
      <figcaption class="text-lg text-lactec-ink-soft">{{ titulo() }}</figcaption>

      <svg viewBox="0 0 160 96" class="w-40 h-24" role="img" [attr.aria-label]="descricao()">
        @for (arco of arcos(); track arco.chave) {
          <path [attr.d]="arco.caminho" [attr.fill]="arco.cor" />
        }
        <line
          [attr.x1]="CENTRO_X"
          [attr.y1]="CENTRO_Y"
          [attr.x2]="ponteiro().x"
          [attr.y2]="ponteiro().y"
          stroke="#0B2230"
          stroke-width="5"
          stroke-linecap="round"
        />
        <circle [attr.cx]="CENTRO_X" [attr.cy]="CENTRO_Y" r="7" fill="#0B2230" />
      </svg>

      <p class="display m-0 text-xl font-semibold tracking-wide text-lactec-ink">
        {{ veredito() }}
      </p>

      <ul class="m-0 flex list-none flex-wrap justify-center gap-x-5 gap-y-1 p-0">
        @for (faixa of faixas; track faixa.chave) {
          <li class="flex flex-col items-center gap-1">
            <span class="flex items-center gap-2 text-sm font-medium">
              <span
                class="inline-block h-3 w-3 rounded-full"
                [style.background]="corDaFaixa(faixa.chave)"
                aria-hidden="true"
              ></span>
              <span [style.color]="corDaFaixa(faixa.chave)">{{ faixa.rotulo }}</span>
            </span>
            <span class="text-xs text-lactec-muted">{{ faixa.criterio }}</span>
          </li>
        }
      </ul>
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConformityGaugeComponent {
  readonly classificacao = input.required<Classificacao>();
  readonly veredito = input.required<string>();
  readonly titulo = input('Conformidade');

  protected readonly faixas = FAIXAS_DE_CONFORMIDADE;
  protected readonly CENTRO_X = CENTRO_X;
  protected readonly CENTRO_Y = CENTRO_Y;

  protected readonly descricao = computed(
    () => `Medidor de conformidade: ${this.veredito()}, faixa ${this.rotuloDaFaixa()}`,
  );

  protected readonly arcos = computed<readonly Arco[]>(() =>
    FAIXAS_DE_CONFORMIDADE.map((faixa, indice) => {
      const de = 180 - indice * 60;
      const ate = de - 60;
      const [x1, y1] = pontoNoArco(de, RAIO);
      const [x2, y2] = pontoNoArco(ate, RAIO);
      const [x3, y3] = pontoNoArco(ate, RAIO * 0.52);
      const [x4, y4] = pontoNoArco(de, RAIO * 0.52);
      return {
        chave: faixa.chave,
        cor: COR_DA_FAIXA[faixa.chave],
        caminho:
          `M ${x1} ${y1} A ${RAIO} ${RAIO} 0 0 1 ${x2} ${y2} ` +
          `L ${x3} ${y3} A ${RAIO * 0.52} ${RAIO * 0.52} 0 0 0 ${x4} ${y4} Z`,
      };
    }),
  );

  protected readonly ponteiro = computed(() => {
    const indice = FAIXAS_DE_CONFORMIDADE.findIndex((faixa) => faixa.chave === this.classificacao());
    const angulo = 180 - (Math.max(indice, 0) * 60 + 30);
    const [x, y] = pontoNoArco(angulo, RAIO * 0.82);
    return { x, y };
  });

  protected corDaFaixa(chave: Classificacao): string {
    return COR_DA_FAIXA[chave];
  }

  private rotuloDaFaixa(): string {
    return (
      FAIXAS_DE_CONFORMIDADE.find((faixa) => faixa.chave === this.classificacao())?.rotulo ?? 'desconhecida'
    );
  }
}
