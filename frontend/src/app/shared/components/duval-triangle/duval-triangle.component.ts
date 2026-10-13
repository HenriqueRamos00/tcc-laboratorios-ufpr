import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import type { TernaryPoint, DuvalTriangle } from '@/app/model/health';

// As três frações somam 100. O vértice de cima é o eixo esquerdo (a), o de
// baixo à direita é o eixo direito (b) e o de baixo à esquerda é a base (c):
//   x = (b + a/2) · largura / 100
//   y = altura − a · altura / 100

const LARGURA = 240;
const ALTURA = 208;

// Uma cor por tipo de falha, como no protótipo Plotly: zona branca só diz onde
// o ponto caiu, zona colorida diz o que a região significa sem ler o rótulo.
// Os tons moram em src/styles.css; aqui ficam as classes utilitárias já
// escritas por extenso, porque o Tailwind só emite a utilitária que encontra
// literal no fonte. A mesma cor pinta duas propriedades diferentes: o
// preenchimento do polígono no desenho e o fundo do quadradinho da legenda.
interface ClassesDaZona {
  readonly preenchimento: string;
  readonly amostra: string;
}

const CLASSES_DA_ZONA: Record<string, ClassesDaZona> = {
  PD: { preenchimento: 'fill-lactec-zona-descargas-parciais', amostra: 'bg-lactec-zona-descargas-parciais' },
  D1: {
    preenchimento: 'fill-lactec-zona-descarga-de-baixa-energia',
    amostra: 'bg-lactec-zona-descarga-de-baixa-energia',
  },
  D2: {
    preenchimento: 'fill-lactec-zona-descarga-de-alta-energia',
    amostra: 'bg-lactec-zona-descarga-de-alta-energia',
  },
  T1: {
    preenchimento: 'fill-lactec-zona-termica-de-baixa-temperatura',
    amostra: 'bg-lactec-zona-termica-de-baixa-temperatura',
  },
  T2: {
    preenchimento: 'fill-lactec-zona-termica-de-media-temperatura',
    amostra: 'bg-lactec-zona-termica-de-media-temperatura',
  },
  T3: {
    preenchimento: 'fill-lactec-zona-termica-de-alta-temperatura',
    amostra: 'bg-lactec-zona-termica-de-alta-temperatura',
  },
  DT: { preenchimento: 'fill-lactec-zona-falha-mista', amostra: 'bg-lactec-zona-falha-mista' },
  S: { preenchimento: 'fill-lactec-zona-gaseificacao-espuria', amostra: 'bg-lactec-zona-gaseificacao-espuria' },
  C: { preenchimento: 'fill-lactec-zona-carbonizacao-do-papel', amostra: 'bg-lactec-zona-carbonizacao-do-papel' },
  O: {
    preenchimento: 'fill-lactec-zona-sobreaquecimento-do-oleo',
    amostra: 'bg-lactec-zona-sobreaquecimento-do-oleo',
  },
  ND: { preenchimento: 'fill-lactec-zona-nao-determinada', amostra: 'bg-lactec-zona-nao-determinada' },
};

const ROTULO_DA_ZONA: Record<string, string> = {
  PD: 'Descargas parciais',
  D1: 'Descarga de baixa energia',
  D2: 'Descarga de alta energia',
  T1: 'Térmica abaixo de 300 °C',
  T2: 'Térmica entre 300 e 700 °C',
  T3: 'Térmica acima de 700 °C',
  DT: 'Mista térmica/elétrica',
  S: 'Gaseificação espúria do óleo',
  C: 'Carbonização do papel',
  O: 'Sobreaquecimento do óleo',
  ND: 'Não determinado',
};

const CLASSES_PADRAO: ClassesDaZona = {
  preenchimento: 'fill-lactec-zona-sem-faixa',
  amostra: 'bg-lactec-zona-sem-faixa',
};

function paraCartesiano(point: TernaryPoint): { x: number; y: number } {
  return {
    x: ((point.b + point.a / 2) * LARGURA) / 100,
    y: ALTURA - (point.a * ALTURA) / 100,
  };
}

interface ZonaDesenhada {
  readonly code: string;
  readonly caminho: string;
  readonly highlighted: boolean;
  readonly classes: ClassesDaZona;
  readonly description: string;
  readonly label: { x: number; y: number };
}

/** Uma entrada da legenda. Zonas em vários anéis (O no triângulo 5) aparecem uma vez. */
interface ItemDaLegenda {
  readonly code: string;
  readonly classes: ClassesDaZona;
  readonly description: string;
  readonly highlighted: boolean;
}

interface PontoDesenhado {
  readonly x: number;
  readonly y: number;
  readonly date: string;
  readonly resumo: string;
}

// O retângulo de foco do navegador fica por cima da figura e atravessa as zonas
// vizinhas, porque o contorno segue a caixa do elemento e não o polígono. Ele sai,
// e o foco continua anunciado de duas formas: o `aria-label` de cada zona e de cada
// leitura, e o realce que `emFoco` e `pontoEmFoco` já aplicam na própria figura, que
// é indicação melhor que a caixa. `outline-hidden` mantém o contorno invisível que o
// modo de alto contraste do sistema ainda desenha; `outline-none` apagaria também lá.
@Component({
  selector: 'app-duval-triangle',
  standalone: true,
  host: { class: 'block' },
  template: `
    <figure class="m-0 flex flex-col items-center gap-2">
      <figcaption class="display text-sm font-semibold tracking-wide text-lactec-ink">
        TRIÂNGULO DE DUVAL {{ triangle().number }}
      </figcaption>

      <svg
        [attr.viewBox]="'-28 -14 ' + (LARGURA + 56) + ' ' + (ALTURA + 46)"
        class="w-full max-w-64"
        [class.opacity-40]="!!triangle().notApplicable"
        role="img"
        [attr.aria-label]="description()"
      >
        <!-- track por índice, não por código: a zona O do triângulo 5 é
             publicada em dois anéis disjuntos e repete o mesmo código. -->
        @for (zona of zones(); track $index) {
          <path
            [attr.d]="zona.caminho"
            [attr.fill-opacity]="opacidadeDa(zona)"
            [attr.stroke-width]="zona.highlighted ? 1.8 : 0.8"
            stroke-linejoin="round"
            class="cursor-pointer stroke-lactec-ink transition-[fill-opacity] duration-100 focus:outline-hidden"
            [class]="zona.classes.preenchimento"
            tabindex="0"
            [attr.aria-label]="zona.code + ': ' + zona.description"
            (mouseenter)="emFoco.set(zona.code)"
            (mouseleave)="emFoco.set(null)"
            (focus)="emFoco.set(zona.code)"
            (blur)="emFoco.set(null)"
          />
          <!-- pointer-events="none": o rótulo fica por cima do path e, sem
               isto, passar o cursor sobre a letra cancela o hover da zona. -->
          <text
            [attr.x]="zona.label.x"
            [attr.y]="zona.label.y"
            text-anchor="middle"
            dominant-baseline="middle"
            font-size="9"
            pointer-events="none"
            class="fill-lactec-ink"
          >
            {{ zona.code }}
          </text>
        }

        @for (point of pontos(); track $index) {
          <circle
            [attr.cx]="point.x"
            [attr.cy]="point.y"
            r="4.5"
            stroke-width="2"
            class="cursor-pointer fill-lactec-primary stroke-lactec-paper focus:outline-hidden"
            tabindex="0"
            [attr.aria-label]="point.date + ', ' + point.resumo"
            (mouseenter)="pontoEmFoco.set(point)"
            (mouseleave)="pontoEmFoco.set(null)"
            (focus)="pontoEmFoco.set(point)"
            (blur)="pontoEmFoco.set(null)"
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
          {{ triangle().leftAxis }}
        </text>
        <text
          [attr.transform]="'rotate(60 ' + (LARGURA + 6) + ' ' + ALTURA / 2 + ')'"
          [attr.x]="LARGURA + 6"
          [attr.y]="ALTURA / 2"
          text-anchor="middle"
          font-size="8"
          class="fill-lactec-ink-soft"
        >
          {{ triangle().rightAxis }}
        </text>
        <text
          [attr.x]="LARGURA / 2"
          [attr.y]="ALTURA + 22"
          text-anchor="middle"
          font-size="8"
          class="fill-lactec-ink-soft"
        >
          ← {{ triangle().baseAxis }}
        </text>
      </svg>

      <!-- Altura fixa: sem ela a legenda pula de lugar a cada mouse-over. -->
      <p class="flex h-8 max-w-64 items-center justify-center px-1 text-center text-xs leading-tight">
        @if (dica(); as texto) {
          <span class="text-lactec-ink">{{ texto }}</span>
        } @else {
          <span class="text-lactec-ink-soft">Passe o cursor sobre uma zona ou leitura</span>
        }
      </p>

      <ul class="grid max-w-64 grid-cols-2 gap-x-2 gap-y-0.5 px-1 text-[10px] leading-tight">
        @for (item of legenda(); track item.code) {
          <li
            class="flex items-center gap-1"
            [class.font-semibold]="item.highlighted"
            (mouseenter)="emFoco.set(item.code)"
            (mouseleave)="emFoco.set(null)"
          >
            <span
              class="inline-block h-2 w-2 shrink-0 rounded-[2px] border border-lactec-ink/40"
              [class]="item.classes.amostra"
            ></span>
            <span class="text-lactec-ink-soft">{{ item.code }}</span>
          </li>
        }
      </ul>

      <!-- Um triângulo sem point precisa dizer por quê. Sem isto, a figura
           vazia passa por "nada foi detectado", que é o oposto do que a norma
           está dizendo. -->
      @if (triangle().notApplicable; as reason) {
        <p class="max-w-64 text-center text-xs text-lactec-ink-soft">
          <span class="font-semibold">Não se aplica.</span> {{ reason }}
        </p>
      }

      <!-- Leitura que não virou ponto precisa dizer por quê, e os dois motivos
           são diferentes: falta de ensaio é do laboratório, soma zero é do
           próprio resultado. -->
      @if (incomputaveis().length) {
        <p class="max-w-64 text-center text-xs text-lactec-ink-soft">
          {{ incomputaveis().length }} leitura(s) sem ensaio de
          {{ gasesAusentes() }}
        </p>
      }
      @if (semProporcao().length) {
        <p class="max-w-64 text-center text-xs text-lactec-ink-soft">
          {{ semProporcao().length }} leitura(s) com os três gases não detectados
        </p>
      }
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuvalTriangleComponent {
  readonly triangle = input.required<DuvalTriangle>();

  protected readonly LARGURA = LARGURA;
  protected readonly ALTURA = ALTURA;

  /** Zona sob o cursor (ou sob o foco do teclado). Espelha o padrão do line-chart. */
  protected readonly emFoco = signal<string | null>(null);
  protected readonly pontoEmFoco = signal<PontoDesenhado | null>(null);

  protected readonly zones = computed<readonly ZonaDesenhada[]>(() =>
    this.triangle().zones.map((zona) => {
      const vertices = zona.vertices.map(paraCartesiano);
      const centro = vertices.reduce(
        (soma, vertice) => ({ x: soma.x + vertice.x / vertices.length, y: soma.y + vertice.y / vertices.length }),
        { x: 0, y: 0 },
      );
      return {
        code: zona.code,
        highlighted: zona.highlighted,
        classes: CLASSES_DA_ZONA[zona.code] ?? CLASSES_PADRAO,
        description: ROTULO_DA_ZONA[zona.code] ?? 'Zona sem descrição catalogada',
        label: centro,
        caminho: vertices.map((vertice, index) => `${index === 0 ? 'M' : 'L'} ${vertice.x} ${vertice.y}`).join(' ') + ' Z',
      };
    }),
  );

  /** Só as coletas computáveis viram marcador; as demais só entram na descrição. */
  protected readonly pontos = computed<readonly PontoDesenhado[]>(() => {
    const triangle = this.triangle();
    // 'Methane CH₄ %' -> 'CH₄': o rótulo do eixo é longo demais para a dica.
    const sigla = (eixo: string) => eixo.replace(/\s*%\s*$/, '').split(' ').pop() ?? eixo;
    const [esquerdo, direito, base] = [
      sigla(triangle.leftAxis),
      sigla(triangle.rightAxis),
      sigla(triangle.baseAxis),
    ];
    const pct = (value: number) => value.toFixed(1).replace('.', ',');
    return triangle.readings
      .filter((leitura) => leitura.kind === 'plotada')
      .map((leitura) => ({
        ...paraCartesiano(leitura.point),
        date: leitura.date,
        resumo:
          `${esquerdo} ${pct(leitura.point.a)}% · ` +
          `${direito} ${pct(leitura.point.b)}% · ` +
          `${base} ${pct(leitura.point.c)}%`,
      }));
  });

  /** Uma entrada por código: a zona O do triângulo 5 tem dois anéis. */
  protected readonly legenda = computed<readonly ItemDaLegenda[]>(() => {
    const vistos = new Map<string, ItemDaLegenda>();
    for (const zona of this.zones()) {
      const existente = vistos.get(zona.code);
      if (existente) {
        if (zona.highlighted && !existente.highlighted) vistos.set(zona.code, { ...existente, highlighted: true });
        continue;
      }
      vistos.set(zona.code, {
        code: zona.code,
        classes: zona.classes,
        description: zona.description,
        highlighted: zona.highlighted,
      });
    }
    return [...vistos.values()];
  });

  /** Os gases que faltaram, sem repetir o mesmo gás em duas leituras. */
  protected readonly gasesAusentes = computed(() =>
    [...new Set(this.incomputaveis().map((leitura) => leitura.missingGas))].join(', '),
  );

  /** A leitura sob o cursor ganha precedência: é o dado, a zona é o contexto. */
  protected readonly dica = computed(() => {
    const point = this.pontoEmFoco();
    if (point) return `${point.date} - ${point.resumo}`;
    const code = this.emFoco();
    if (!code) return '';
    const zona = this.zones().find((z) => z.code === code);
    return zona ? `${zona.code} - ${zona.description}` : '';
  });

  /** Zona em foco salta; zona do diagnóstico fica sólida; o resto recua. */
  protected opacidadeDa(zona: ZonaDesenhada): number {
    if (this.emFoco() === zona.code) return 1;
    return zona.highlighted ? 0.95 : 0.45;
  }

  protected readonly incomputaveis = computed(() =>
    this.triangle().readings.filter((leitura) => leitura.kind === 'incomputavel'),
  );

  protected readonly naoAplicaveis = computed(() =>
    this.triangle().readings.filter((leitura) => leitura.kind === 'naoAplicavel'),
  );

  /**
   * Leitura em que os três gases da figura deram não detectado. Não falta
   * ensaio e não falta licença: falta proporção, porque a figura é a razão
   * entre os três e a razão de zero por zero não é ponto.
   */
  protected readonly semProporcao = computed(() =>
    this.triangle().readings.filter((leitura) => leitura.kind === 'semProporcao'),
  );

  protected readonly description = computed(() => {
    const triangle = this.triangle();
    const destacadas = triangle.zones.filter((zona) => zona.highlighted).map((zona) => zona.code);
    const incomputaveis = this.incomputaveis();
    const naoAplicaveis = this.naoAplicaveis();
    const semProporcao = this.semProporcao();
    // Zonas repetem código quando a zona tem mais de um anel (O no triângulo
    // 5); o leitor de tela não precisa ouvir "O, O".
    const codigosUnicos = [...new Set(destacadas)];
    return (
      `Triângulo de Duval ${triangle.number}, eixos ${triangle.leftAxis}, ${triangle.rightAxis} e ${triangle.baseAxis}. ` +
      `${this.pontos().length} de ${triangle.readings.length} leitura(s) plotada(s)` +
      `${incomputaveis.length ? `, ${incomputaveis.length} sem ${[...new Set(incomputaveis.map((leitura) => leitura.missingGas))].join('/')}` : ''}` +
      `${semProporcao.length ? `, ${semProporcao.length} com os três gases não detectados` : ''}` +
      `${naoAplicaveis.length ? `, ${naoAplicaveis.length} fora da faixa de aplicação desta figura` : ''}. ` +
      `${triangle.notApplicable ? `Figura não aplicável: ${triangle.notApplicable}. ` : ''}` +
      `Zonas em destaque: ${codigosUnicos.join(', ') || 'nenhuma'}.`
    );
  });
}
