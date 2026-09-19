import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

// A ordem de ZONAS segue a tela: T2 no topo e as demais em sentido horário.

const RAIO = 84;
const CENTRO = 100;
const ZONAS = ['T2', 'D1', 'D2', 'PD', 'T1'] as const;

export type ZonaDoPentagono = (typeof ZONAS)[number];

function vertice(indice: number, raio: number): { x: number; y: number } {
  const angulo = (Math.PI * 2 * indice) / 5 - Math.PI / 2;
  return { x: CENTRO + raio * Math.cos(angulo), y: CENTRO + raio * Math.sin(angulo) };
}

@Component({
  selector: 'app-duval-pentagon',
  standalone: true,
  host: { class: 'block' },
  template: `
    <figure class="m-0 flex flex-col items-center gap-2">
      <figcaption class="display text-sm font-semibold tracking-wide text-lactec-ink">
        PENTÁGONO DE DUVAL
      </figcaption>

      <svg viewBox="0 0 200 200" class="w-full max-w-52" role="img" [attr.aria-label]="descricao()">
        <path [attr.d]="contorno()" fill="#FFFFFF" stroke="#0B2230" stroke-width="2.5" />

        @for (setor of setores(); track setor.codigo) {
          <path [attr.d]="setor.divisoria" stroke="#B8C2C7" stroke-width="1" fill="none" />
          <text
            [attr.x]="setor.rotulo.x"
            [attr.y]="setor.rotulo.y"
            text-anchor="middle"
            dominant-baseline="middle"
            font-size="15"
            font-weight="700"
            class="fill-lactec-ink"
          >
            {{ setor.codigo }}
          </text>
        }

        <circle
          [attr.cx]="leitura().x"
          [attr.cy]="leitura().y"
          r="6"
          fill="#1F6B44"
          stroke="#FFFFFF"
          stroke-width="2"
        />
      </svg>
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuvalPentagonComponent {
  /** Zona classificada e posição da leitura, em coordenadas de -1 a 1. */
  readonly zona = input.required<ZonaDoPentagono>();
  readonly x = input(0);
  readonly y = input(0);

  protected readonly contorno = computed(
    () =>
      ZONAS.map((_, indice) => {
        const ponta = vertice(indice, RAIO);
        return `${indice === 0 ? 'M' : 'L'} ${ponta.x} ${ponta.y}`;
      }).join(' ') + ' Z',
  );

  protected readonly setores = computed(() =>
    ZONAS.map((codigo, indice) => {
      const ponta = vertice(indice, RAIO);
      const meio = vertice(indice + 0.5, RAIO * 0.62);
      return {
        codigo,
        divisoria: `M ${CENTRO} ${CENTRO} L ${ponta.x} ${ponta.y}`,
        rotulo: meio,
      };
    }),
  );

  protected readonly leitura = computed(() => ({
    x: CENTRO + this.x() * RAIO * 0.6,
    y: CENTRO + this.y() * RAIO * 0.6,
  }));

  protected readonly descricao = computed(
    () => `Pentágono de Duval com a leitura classificada na zona ${this.zona()}.`,
  );
}
