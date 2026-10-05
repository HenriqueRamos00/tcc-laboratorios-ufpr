import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import type { PontoTernario, TrianguloDeDuval } from '@/app/model/health';

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
  preenchimento: 'fill-lactec-zona-sem-catalogo',
  amostra: 'bg-lactec-zona-sem-catalogo',
};

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
  readonly classes: ClassesDaZona;
  readonly descricao: string;
  readonly rotulo: { x: number; y: number };
}

/** Uma entrada da legenda. Zonas em vários anéis (O no triângulo 5) aparecem uma vez. */
interface ItemDaLegenda {
  readonly codigo: string;
  readonly classes: ClassesDaZona;
  readonly descricao: string;
  readonly destacada: boolean;
}

interface PontoDesenhado {
  readonly x: number;
  readonly y: number;
  readonly data: string;
  readonly resumo: string;
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
        [class.opacity-40]="!!triangulo().naoAplicavel"
        role="img"
        [attr.aria-label]="descricao()"
      >
        <!-- track por índice, não por código: a zona O do triângulo 5 é
             publicada em dois anéis disjuntos e repete o mesmo código. -->
        @for (zona of zonas(); track $index) {
          <path
            [attr.d]="zona.caminho"
            [attr.fill-opacity]="opacidadeDa(zona)"
            [attr.stroke-width]="zona.destacada ? 1.8 : 0.8"
            stroke-linejoin="round"
            class="cursor-pointer stroke-lactec-ink transition-[fill-opacity] duration-100"
            [class]="zona.classes.preenchimento"
            tabindex="0"
            [attr.aria-label]="zona.codigo + ': ' + zona.descricao"
            (mouseenter)="emFoco.set(zona.codigo)"
            (mouseleave)="emFoco.set(null)"
            (focus)="emFoco.set(zona.codigo)"
            (blur)="emFoco.set(null)"
          />
          <!-- pointer-events="none": o rótulo fica por cima do path e, sem
               isto, passar o cursor sobre a letra cancela o hover da zona. -->
          <text
            [attr.x]="zona.rotulo.x"
            [attr.y]="zona.rotulo.y"
            text-anchor="middle"
            dominant-baseline="middle"
            font-size="9"
            pointer-events="none"
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
            stroke-width="2"
            class="cursor-pointer fill-lactec-primary stroke-lactec-paper"
            tabindex="0"
            [attr.aria-label]="ponto.data + ', ' + ponto.resumo"
            (mouseenter)="pontoEmFoco.set(ponto)"
            (mouseleave)="pontoEmFoco.set(null)"
            (focus)="pontoEmFoco.set(ponto)"
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

      <!-- Altura fixa: sem ela a legenda pula de lugar a cada mouse-over. -->
      <p class="flex h-8 max-w-64 items-center justify-center px-1 text-center text-xs leading-tight">
        @if (dica(); as texto) {
          <span class="text-lactec-ink">{{ texto }}</span>
        } @else {
          <span class="text-lactec-ink-soft">Passe o cursor sobre uma zona ou leitura</span>
        }
      </p>

      <ul class="grid max-w-64 grid-cols-2 gap-x-2 gap-y-0.5 px-1 text-[10px] leading-tight">
        @for (item of legenda(); track item.codigo) {
          <li
            class="flex items-center gap-1"
            [class.font-semibold]="item.destacada"
            (mouseenter)="emFoco.set(item.codigo)"
            (mouseleave)="emFoco.set(null)"
          >
            <span
              class="inline-block h-2 w-2 shrink-0 rounded-[2px] border border-lactec-ink/40"
              [class]="item.classes.amostra"
            ></span>
            <span class="text-lactec-ink-soft">{{ item.codigo }}</span>
          </li>
        }
      </ul>

      <!-- Um triângulo sem ponto precisa dizer por quê. Sem isto, a figura
           vazia passa por "nada foi detectado", que é o oposto do que a norma
           está dizendo. -->
      @if (triangulo().naoAplicavel; as motivo) {
        <p class="max-w-64 text-center text-xs text-lactec-ink-soft">
          <span class="font-semibold">Não se aplica.</span> {{ motivo }}
        </p>
      }
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuvalTriangleComponent {
  readonly triangulo = input.required<TrianguloDeDuval>();

  protected readonly LARGURA = LARGURA;
  protected readonly ALTURA = ALTURA;

  /** Zona sob o cursor (ou sob o foco do teclado). Espelha o padrão do line-chart. */
  protected readonly emFoco = signal<string | null>(null);
  protected readonly pontoEmFoco = signal<PontoDesenhado | null>(null);

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
        classes: CLASSES_DA_ZONA[zona.codigo] ?? CLASSES_PADRAO,
        descricao: ROTULO_DA_ZONA[zona.codigo] ?? 'Zona sem descrição catalogada',
        rotulo: centro,
        caminho: vertices.map((vertice, indice) => `${indice === 0 ? 'M' : 'L'} ${vertice.x} ${vertice.y}`).join(' ') + ' Z',
      };
    }),
  );

  /** Só as coletas computáveis viram marcador; as demais só entram na descrição. */
  protected readonly pontos = computed<readonly PontoDesenhado[]>(() => {
    const triangulo = this.triangulo();
    // 'Methane CH₄ %' -> 'CH₄': o rótulo do eixo é longo demais para a dica.
    const sigla = (eixo: string) => eixo.replace(/\s*%\s*$/, '').split(' ').pop() ?? eixo;
    const [esquerdo, direito, base] = [
      sigla(triangulo.eixoEsquerdo),
      sigla(triangulo.eixoDireito),
      sigla(triangulo.eixoBase),
    ];
    const pct = (valor: number) => valor.toFixed(1).replace('.', ',');
    return triangulo.leituras
      .filter((leitura) => leitura.tipo === 'plotada')
      .map((leitura) => ({
        ...paraCartesiano(leitura.ponto),
        data: leitura.data,
        resumo:
          `${esquerdo} ${pct(leitura.ponto.a)}% · ` +
          `${direito} ${pct(leitura.ponto.b)}% · ` +
          `${base} ${pct(leitura.ponto.c)}%`,
      }));
  });

  /** Uma entrada por código: a zona O do triângulo 5 tem dois anéis. */
  protected readonly legenda = computed<readonly ItemDaLegenda[]>(() => {
    const vistos = new Map<string, ItemDaLegenda>();
    for (const zona of this.zonas()) {
      const existente = vistos.get(zona.codigo);
      if (existente) {
        if (zona.destacada && !existente.destacada) vistos.set(zona.codigo, { ...existente, destacada: true });
        continue;
      }
      vistos.set(zona.codigo, {
        codigo: zona.codigo,
        classes: zona.classes,
        descricao: zona.descricao,
        destacada: zona.destacada,
      });
    }
    return [...vistos.values()];
  });

  /** A leitura sob o cursor ganha precedência: é o dado, a zona é o contexto. */
  protected readonly dica = computed(() => {
    const ponto = this.pontoEmFoco();
    if (ponto) return `${ponto.data} - ${ponto.resumo}`;
    const codigo = this.emFoco();
    if (!codigo) return '';
    const zona = this.zonas().find((z) => z.codigo === codigo);
    return zona ? `${zona.codigo} - ${zona.descricao}` : '';
  });

  /** Zona em foco salta; zona do diagnóstico fica sólida; o resto recua. */
  protected opacidadeDa(zona: ZonaDesenhada): number {
    if (this.emFoco() === zona.codigo) return 1;
    return zona.destacada ? 0.95 : 0.45;
  }

  protected readonly incomputaveis = computed(() =>
    this.triangulo().leituras.filter((leitura) => leitura.tipo === 'incomputavel'),
  );

  protected readonly naoAplicaveis = computed(() =>
    this.triangulo().leituras.filter((leitura) => leitura.tipo === 'naoAplicavel'),
  );

  protected readonly descricao = computed(() => {
    const triangulo = this.triangulo();
    const destacadas = triangulo.zonas.filter((zona) => zona.destacada).map((zona) => zona.codigo);
    const incomputaveis = this.incomputaveis();
    const naoAplicaveis = this.naoAplicaveis();
    // Zonas repetem código quando a zona tem mais de um anel (O no triângulo
    // 5); o leitor de tela não precisa ouvir "O, O".
    const codigosUnicos = [...new Set(destacadas)];
    return (
      `Triângulo de Duval ${triangulo.numero}, eixos ${triangulo.eixoEsquerdo}, ${triangulo.eixoDireito} e ${triangulo.eixoBase}. ` +
      `${this.pontos().length} de ${triangulo.leituras.length} leitura(s) plotada(s)` +
      `${incomputaveis.length ? `, ${incomputaveis.length} sem ${[...new Set(incomputaveis.map((leitura) => leitura.gasAusente))].join('/')}` : ''}` +
      `${naoAplicaveis.length ? `, ${naoAplicaveis.length} fora da faixa de aplicação desta figura` : ''}. ` +
      `${triangulo.naoAplicavel ? `Figura não aplicável: ${triangulo.naoAplicavel}. ` : ''}` +
      `Zonas em destaque: ${codigosUnicos.join(', ') || 'nenhuma'}.`
    );
  });
}
