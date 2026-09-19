import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import type { PontoTernario, TrianguloDeDuval } from '@/app/model/health';

// As três frações somam 100. O vértice de cima é o eixo esquerdo (a), o de
// baixo à direita é o eixo direito (b) e o de baixo à esquerda é a base (c):
//   x = (b + a/2) · largura / 100
//   y = altura − a · altura / 100

const LARGURA = 240;
const ALTURA = 208;

function paraCartesiano(ponto: PontoTernario): { x: number; y: number } {
  return {
    x: ((ponto.b + ponto.a / 2) * LARGURA) / 100,
    y: ALTURA - (ponto.a * ALTURA) / 100,
  };
}

interface ZonaDesenhada {
  readonly codigo: string;
  readonly caminho: string;
  readonly destacada: boolean;
  readonly rotulo: { x: number; y: number };
}

@Component({
  selector: 'app-duval-triangle',
  standalone: true,
  host: { class: 'block' },
  template: `
    <figure class="m-0 flex flex-col items-center gap-2">
      <figcaption class="display text-sm font-semibold tracking-wide text-lactec-ink">
        TRIÂNGULO DE DUVAL {{ triangulo().numero }}
      </figcaption>

      <svg
        [attr.viewBox]="'-28 -14 ' + (LARGURA + 56) + ' ' + (ALTURA + 46)"
        class="w-full max-w-64"
        role="img"
        [attr.aria-label]="descricao()"
      >
        @for (zona of zonas(); track zona.codigo) {
          <path
            [attr.d]="zona.caminho"
            [attr.fill]="zona.destacada ? '#F0A22C' : '#FFFFFF'"
            stroke="#0B2230"
            stroke-width="1"
            stroke-linejoin="round"
          />
          <text
            [attr.x]="zona.rotulo.x"
            [attr.y]="zona.rotulo.y"
            text-anchor="middle"
            dominant-baseline="middle"
            font-size="9"
            class="fill-lactec-ink"
          >
            {{ zona.codigo }}
          </text>
        }

        @for (ponto of pontos(); track $index) {
          <circle
            [attr.cx]="ponto.x"
            [attr.cy]="ponto.y"
            r="4.5"
            fill="#18A5B8"
            stroke="#FFFFFF"
            stroke-width="2"
          />
        }

        <text
          [attr.transform]="'rotate(-60 -6 ' + ALTURA / 2 + ')'"
          x="-6"
          [attr.y]="ALTURA / 2"
          text-anchor="middle"
          font-size="8"
          class="fill-lactec-ink-soft"
        >
          {{ triangulo().eixoEsquerdo }}
        </text>
        <text
          [attr.transform]="'rotate(60 ' + (LARGURA + 6) + ' ' + ALTURA / 2 + ')'"
          [attr.x]="LARGURA + 6"
          [attr.y]="ALTURA / 2"
          text-anchor="middle"
          font-size="8"
          class="fill-lactec-ink-soft"
        >
          {{ triangulo().eixoDireito }}
        </text>
        <text
          [attr.x]="LARGURA / 2"
          [attr.y]="ALTURA + 22"
          text-anchor="middle"
          font-size="8"
          class="fill-lactec-ink-soft"
        >
          ← {{ triangulo().eixoBase }}
        </text>
      </svg>
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuvalTriangleComponent {
  readonly triangulo = input.required<TrianguloDeDuval>();

  protected readonly LARGURA = LARGURA;
  protected readonly ALTURA = ALTURA;

  protected readonly zonas = computed<readonly ZonaDesenhada[]>(() =>
    this.triangulo().zonas.map((zona) => {
      const vertices = zona.vertices.map(paraCartesiano);
      const centro = vertices.reduce(
        (soma, vertice) => ({ x: soma.x + vertice.x / vertices.length, y: soma.y + vertice.y / vertices.length }),
        { x: 0, y: 0 },
      );
      return {
        codigo: zona.codigo,
        destacada: zona.destacada,
        rotulo: centro,
        caminho: vertices.map((vertice, indice) => `${indice === 0 ? 'M' : 'L'} ${vertice.x} ${vertice.y}`).join(' ') + ' Z',
      };
    }),
  );

  protected readonly pontos = computed(() => this.triangulo().pontos.map(paraCartesiano));

  protected readonly descricao = computed(() => {
    const triangulo = this.triangulo();
    const destacadas = triangulo.zonas.filter((zona) => zona.destacada).map((zona) => zona.codigo);
    return (
      `Triângulo de Duval ${triangulo.numero}, eixos ${triangulo.eixoEsquerdo}, ${triangulo.eixoDireito} e ${triangulo.eixoBase}. ` +
      `${triangulo.pontos.length} leitura(s) plotada(s). Zonas em destaque: ${destacadas.join(', ') || 'nenhuma'}.`
    );
  });
}
