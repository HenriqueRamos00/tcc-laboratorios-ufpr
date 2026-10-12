import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { DESCRICAO_DE_DUVAL_1, type CodigoDeDuval1 } from '@/app/model/duval-1';

// A ordem de ZONAS segue a tela: T2 no topo e as demais em sentido horário.
//
// ATENÇÃO: estes cinco setores são o desenho do protótipo, não a tabela da
// norma, que publica sete zonas (PD, D1, D2, T1, T2, T3 e S). O componente
// desenha e posiciona o marcador; quem classifica a zona é quem passa o input, e
// hoje ninguém classifica, porque a tabela de fronteiras não está no projeto.
// Por isso `zone` aceita nulo: figura sem zona atribuída é o caso normal aqui, e
// não falta de valor a ser preenchida com um palpite.

const RAIO = 84;
const CENTRO = 100;
const ZONAS = ['T2', 'D1', 'D2', 'PD', 'T1'] as const;

export type PentagonZone = (typeof ZONAS)[number];

// PROVENIÊNCIA: a descrição de cada zona vem do dicionário do triângulo de Duval 1,
// onde os mesmos códigos já estão escritos. O texto do pentágono NÃO foi lido de
// fonte citável, e esta reutilização é de vocabulário, não de fronteira: o que cada
// sigla significa (descarga parcial, falha térmica, descarga de baixa energia) é o
// mesmo nos dois métodos. O que muda entre eles é onde cada zona começa e termina,
// e essa tabela continua ausente do projeto, por isso a zona não é atribuída aqui.
const DESCRICAO_DA_ZONA: Record<PentagonZone, string> =
  DESCRICAO_DE_DUVAL_1 as Record<CodigoDeDuval1, string>;

/** Um gás do pentágono, com o percentual já no formato que o cliente lê. */
export interface PentagonGasReading {
  readonly gas: string;
  readonly text: string;
}

function vertice(indice: number, raio: number): { x: number; y: number } {
  const angulo = (Math.PI * 2 * indice) / 5 - Math.PI / 2;

  return { x: CENTRO + raio * Math.cos(angulo), y: CENTRO + raio * Math.sin(angulo) };
}

/**
 * O pentágono de Duval, com o mesmo comportamento das três figuras ternárias da
 * tela: setor e leitura respondem ao cursor, a dica fica numa linha de altura fixa
 * e a legenda abaixo reage ao cursor como a dos triângulos.
 *
 * A legenda aqui é a dos gases, e não a das zonas, porque é o percentual de cada
 * gás que posiciona o ponto: apontar o gás acende o eixo dele, que é a pergunta que
 * quem lê a figura faz. As zonas continuam nomeadas dentro do desenho.
 */
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
        <path [attr.d]="contorno()" class="fill-lactec-paper stroke-lactec-ink" stroke-width="2.5" />

        @for (setor of setores(); track setor.codigo) {
          <path
            [attr.d]="setor.fatia"
            class="cursor-pointer fill-lactec-primary stroke-none transition-[fill-opacity]
                   duration-100 focus:outline-hidden"
            [attr.fill-opacity]="setor.codigo === emFoco() ? 0.16 : 0"
            tabindex="0"
            [attr.aria-label]="setor.codigo + ': ' + setor.descricao"
            (mouseenter)="emFoco.set(setor.codigo)"
            (mouseleave)="emFoco.set(null)"
            (focus)="emFoco.set(setor.codigo)"
            (blur)="emFoco.set(null)"
          />
          <path [attr.d]="setor.divisoria" class="fill-none stroke-lactec-divider" stroke-width="1" />
          <!-- pointer-events="none": sem isto, o rótulo cancela o hover do setor. -->
          <text
            [attr.x]="setor.rotulo.x"
            [attr.y]="setor.rotulo.y"
            text-anchor="middle"
            dominant-baseline="middle"
            font-size="15"
            font-weight="700"
            pointer-events="none"
            class="fill-lactec-ink"
            [class.font-black]="setor.codigo === emFoco()"
          >
            {{ setor.codigo }}
          </text>
        }

        @for (eixo of eixos(); track eixo.gas) {
          <line
            [attr.x1]="CENTRO"
            [attr.y1]="CENTRO"
            [attr.x2]="eixo.x"
            [attr.y2]="eixo.y"
            class="stroke-lactec-primary transition-opacity duration-100"
            stroke-width="2"
            [style.opacity]="eixo.gas === gasEmFoco() ? 1 : 0"
          />
        }

        <circle
          [attr.cx]="leitura().x"
          [attr.cy]="leitura().y"
          r="6"
          class="fill-lactec-tarja-normal stroke-lactec-paper focus:outline-hidden"
          stroke-width="2"
          tabindex="0"
          [attr.aria-label]="descricao()"
          (mouseenter)="leituraEmFoco.set(true)"
          (mouseleave)="leituraEmFoco.set(false)"
          (focus)="leituraEmFoco.set(true)"
          (blur)="leituraEmFoco.set(false)"
        />
      </svg>

      <!-- Altura fixa: sem ela a legenda pula de lugar a cada mouse-over. -->
      <p class="flex h-8 max-w-64 items-center justify-center px-1 text-center text-xs leading-tight">
        @if (dica(); as texto) {
          <span class="text-lactec-ink">{{ texto }}</span>
        } @else {
          <span class="text-lactec-ink-soft">Passe o cursor sobre uma zona ou leitura</span>
        }
      </p>

      @if (percentages().length) {
        <ul class="grid max-w-64 grid-cols-5 gap-x-2 gap-y-0.5 px-1 text-center text-[10px] leading-tight">
          @for (item of percentages(); track item.gas) {
            <li
              class="cursor-default"
              [class.font-semibold]="item.gas === gasEmFoco()"
              (mouseenter)="gasEmFoco.set(item.gas)"
              (mouseleave)="gasEmFoco.set(null)"
            >
              <span class="block text-lactec-muted">{{ item.gas }}</span>
              <span class="block text-lactec-ink">{{ item.text }}</span>
            </li>
          }
        </ul>
      }
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuvalPentagonComponent {
  /** Zona classificada, quando houver, e posição da leitura, em coordenadas de -1 a 1. */
  readonly zone = input<PentagonZone | null>(null);
  readonly x = input(0);
  readonly y = input(0);
  readonly percentages = input<readonly PentagonGasReading[]>([]);

  protected readonly CENTRO = CENTRO;

  protected readonly emFoco = signal<string | null>(null);
  protected readonly gasEmFoco = signal<string | null>(null);
  protected readonly leituraEmFoco = signal(false);

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
      const seguinte = vertice(indice + 1, RAIO);

      return {
        codigo,
        descricao: DESCRICAO_DA_ZONA[codigo],
        fatia: `M ${CENTRO} ${CENTRO} L ${ponta.x} ${ponta.y} L ${seguinte.x} ${seguinte.y} Z`,
        divisoria: `M ${CENTRO} ${CENTRO} L ${ponta.x} ${ponta.y}`,
        rotulo: vertice(indice + 0.5, RAIO * 0.62),
      };
    }),
  );

  /** Um eixo por gás, na ordem em que os percentuais chegam, acendido pela legenda. */
  protected readonly eixos = computed(() =>
    this.percentages().map((item, indice) => ({ gas: item.gas, ...vertice(indice, RAIO) })),
  );

  protected readonly leitura = computed(() => ({
    x: CENTRO + this.x() * RAIO * 0.6,
    y: CENTRO + this.y() * RAIO * 0.6,
  }));

  /**
   * A dica diz o que a zona significa, e não só o nome dela. "Zona D1" não informa
   * nada a quem está aprendendo a ler a figura, que é justamente quem passa o cursor.
   */
  protected readonly dica = computed(() => {
    if (this.leituraEmFoco()) return this.descricao();

    const zona = this.emFoco() as PentagonZone | null;

    return zona ? `${zona}: ${DESCRICAO_DA_ZONA[zona]}` : null;
  });

  protected readonly descricao = computed(() => {
    const zone = this.zone();

    return zone
      ? `Pentágono de Duval com a leitura classificada na zona ${zone}.`
      : 'Pentágono de Duval com a leitura posicionada e a zona não atribuída.';
  });
}
