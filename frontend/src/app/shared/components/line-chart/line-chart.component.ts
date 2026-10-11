import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

// A escala logarítmica existe porque a cromatografia tem o nitrogênio quatro
// ordens de grandeza acima do acetileno e some numa escala linear.
//
// Identidade nunca vem só da cor: cada série tem uma forma de marcador,
// repetida na legenda, e o tooltip do cruzamento mostra todas de uma vez.

export interface ChartSeries {
  readonly key: string;
  readonly name: string;
  /** Índice na paleta categórica; sem ciclo automático. */
  readonly palettePosition: number;
  readonly values: readonly (number | null)[];
}

export type AxisScale = 'linear' | 'log';

// Paleta categórica validada para superfície clara (checagem de visão de
// cores, faixa de luminosidade e piso de contraste). A ordem é fixa.
//
// Cada posição guarda a classe utilitária inteira, uma por propriedade, em vez
// do tom: o traço da linha pinta `stroke` e o marcador pinta `fill`, e o
// Tailwind só emite a utilitária que encontra escrita por extenso no fonte.
// Nome montado em tempo de execução não chega ao CSS gerado.
interface ClassesDaSerie {
  readonly traco: string;
  readonly marcador: string;
}

const PALETA: readonly ClassesDaSerie[] = [
  { traco: 'stroke-lactec-serie-primeira', marcador: 'fill-lactec-serie-primeira' },
  { traco: 'stroke-lactec-serie-segunda', marcador: 'fill-lactec-serie-segunda' },
  { traco: 'stroke-lactec-serie-terceira', marcador: 'fill-lactec-serie-terceira' },
  { traco: 'stroke-lactec-serie-quarta', marcador: 'fill-lactec-serie-quarta' },
  { traco: 'stroke-lactec-serie-quinta', marcador: 'fill-lactec-serie-quinta' },
  { traco: 'stroke-lactec-serie-sexta', marcador: 'fill-lactec-serie-sexta' },
  { traco: 'stroke-lactec-serie-setima', marcador: 'fill-lactec-serie-setima' },
  { traco: 'stroke-lactec-serie-oitava', marcador: 'fill-lactec-serie-oitava' },
];

const FORMAS = ['circulo', 'quadrado', 'triangulo', 'losango'] as const;
type Forma = (typeof FORMAS)[number];

const LARGURA = 720;
const ALTURA = 320;
const MARGEM = { topo: 16, direita: 16, base: 46, esquerda: 56 };

interface PontoDesenhado {
  readonly x: number;
  readonly y: number;
}

interface SerieDesenhada {
  readonly key: string;
  readonly name: string;
  readonly classes: ClassesDaSerie;
  readonly forma: Forma;
  readonly tracejada: boolean;
  readonly caminho: string;
  readonly pontos: readonly PontoDesenhado[];
}

// Lacuna de coleta nao e "nao detectado": o ensaio nao foi feito naquela data.
// A tabela de indicadores marca a mesma ausencia do mesmo jeito.
const MARCA_DE_COLETA_NAO_REALIZADA = '-';

@Component({
  selector: 'app-line-chart',
  standalone: true,
  host: { class: 'block' },
  templateUrl: './line-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineChartComponent {
  readonly title = input.required<string>();
  readonly xAxisLabels = input.required<readonly string[]>();
  readonly series = input.required<readonly ChartSeries[]>();
  readonly scale = input<AxisScale>('linear');
  readonly yAxisLabel = input('Valor');
  readonly xAxisLabel = input('Data');

  protected readonly LARGURA = LARGURA;
  protected readonly ALTURA = ALTURA;
  protected readonly MARGEM = MARGEM;

  protected readonly indiceEmFoco = signal<number | null>(null);

  private readonly limites = computed(() => {
    const values = this.series()
      .flatMap((serie) => serie.values)
      .filter(
        (value): value is number =>
          value !== null &&
          Number.isFinite(value) &&
          (this.scale() === 'linear' || value > 0),
      );

    // Sem nenhum valor plotável não há faixa para calcular, e a faixa vazia precisa ser
    // válida para a escala em uso. Em escala logarítmica o zero não tem logaritmo:
    // `Math.log10(0)` é `-Infinity`, a contagem de marcas do eixo virava `Infinity` e
    // `Array.from` morria com `RangeError`. O estrago não parava no gráfico, porque o erro
    // acontecia durante a detecção de mudanças: o ciclo inteiro abortava, o `ngDoCheck` do
    // container do menu deixava de rodar e a barra lateral parava de recalcular a margem do
    // conteúdo. Medido com um laudo sem nenhum gás dissolvido, na aba de gases.
    if (!values.length) {
      return this.scale() === 'log' ? { minimo: 1, maximo: 10 } : { minimo: 0, maximo: 1 };
    }

    const bruto = { minimo: Math.min(...values), maximo: Math.max(...values) };
    if (this.scale() === 'log') {
      return {
        minimo: Math.pow(10, Math.floor(Math.log10(bruto.minimo))),
        maximo: Math.pow(10, Math.ceil(Math.log10(bruto.maximo))),
      };
    }
    const folga = (bruto.maximo - bruto.minimo) * 0.1 || 1;
    return { minimo: Math.max(0, bruto.minimo - folga), maximo: bruto.maximo + folga };
  });

  protected readonly marcasDoEixoY = computed(() => {
    const { minimo, maximo } = this.limites();
    if (this.scale() === 'log') {
      const de = Math.log10(minimo);
      const ate = Math.log10(maximo);
      // Cinto além da trava acima: nenhuma faixa de entrada pode virar um número de marcas
      // que `Array.from` recuse. Uma década por marca, no mínimo duas e no máximo dez.
      const decadas = Math.round(ate - de);
      const quantidade = Number.isFinite(decadas) ? Math.min(Math.max(decadas, 1), 9) + 1 : 2;
      return Array.from({ length: quantidade }, (_, passo) => {
        const value = Math.pow(10, de + passo);
        return { value, y: this.posicaoY(value), rotulo: this.rotuloLog(de + passo) };
      });
    }
    return Array.from({ length: 6 }, (_, marca) => {
      const value = minimo + ((maximo - minimo) * marca) / 5;
      return { value, y: this.posicaoY(value), rotulo: this.formatarNumero(value) };
    });
  });

  protected readonly marcasDoEixoX = computed(() =>
    this.xAxisLabels().map((rotulo, indice) => ({ rotulo, x: this.posicaoX(indice) })),
  );

  protected readonly seriesDesenhadas = computed<readonly SerieDesenhada[]>(() =>
    this.series().map((serie, indice) => {
      const pontos: PontoDesenhado[] = [];
      const trechos: string[] = [];
      let abrindoTrecho = true;

      serie.values.forEach((value, posicao) => {
        if (value === null || (this.scale() === 'log' && value <= 0)) {
          abrindoTrecho = true;
          return;
        }
        const ponto = { x: this.posicaoX(posicao), y: this.posicaoY(value) };
        pontos.push(ponto);
        trechos.push(`${abrindoTrecho ? 'M' : 'L'} ${ponto.x} ${ponto.y}`);
        abrindoTrecho = false;
      });

      return {
        key: serie.key,
        name: serie.name,
        classes: PALETA[serie.palettePosition % PALETA.length],
        forma: FORMAS[Math.floor(indice / PALETA.length) % FORMAS.length],
        // A partir da nona série a cor se repete; o traço passa a tracejado
        // para que a identidade continue distinguível sem inventar matiz nova.
        tracejada: indice >= PALETA.length,
        caminho: trechos.join(' '),
        pontos,
      };
    }),
  );

  protected readonly leituraEmFoco = computed(() => {
    const indice = this.indiceEmFoco();
    if (indice === null) return null;
    return {
      x: this.posicaoX(indice),
      rotulo: this.xAxisLabels()[indice],
      values: this.series().map((serie, ordem) => ({
        name: serie.name,
        classes: PALETA[serie.palettePosition % PALETA.length],
        forma: FORMAS[Math.floor(ordem / PALETA.length) % FORMAS.length],
        value: serie.values[indice],
      })),
    };
  });

  protected aoMoverPonteiro(evento: MouseEvent): void {
    const alvo = evento.currentTarget as SVGGraphicsElement;
    const caixa = alvo.getBoundingClientRect();
    if (!caixa.width) return;
    const proporcao = (evento.clientX - caixa.left) / caixa.width;
    const dentro = proporcao * LARGURA;
    const total = this.xAxisLabels().length;
    if (total < 2) {
      this.indiceEmFoco.set(total === 1 ? 0 : null);
      return;
    }
    const passo = (LARGURA - MARGEM.esquerda - MARGEM.direita) / (total - 1);
    const indice = Math.round((dentro - MARGEM.esquerda) / passo);
    this.indiceEmFoco.set(Math.min(Math.max(indice, 0), total - 1));
  }

  protected aoSairDoGrafico(): void {
    this.indiceEmFoco.set(null);
  }

  protected marcador(forma: Forma, x: number, y: number): string {
    const raio = 4.5;
    switch (forma) {
      case 'quadrado':
        return `M ${x - raio} ${y - raio} H ${x + raio} V ${y + raio} H ${x - raio} Z`;
      case 'triangulo':
        return `M ${x} ${y - raio - 1} L ${x + raio + 1} ${y + raio} L ${x - raio - 1} ${y + raio} Z`;
      case 'losango':
        return `M ${x} ${y - raio - 1} L ${x + raio + 1} ${y} L ${x} ${y + raio + 1} L ${x - raio - 1} ${y} Z`;
      default:
        return `M ${x - raio} ${y} a ${raio} ${raio} 0 1 0 ${raio * 2} 0 a ${raio} ${raio} 0 1 0 ${-raio * 2} 0`;
    }
  }

  protected formatarNumero(value: number | null): string {
    if (value === null) return MARCA_DE_COLETA_NAO_REALIZADA;
    if (Math.abs(value) >= 1000) return value.toLocaleString('pt-BR');
    return Number.isInteger(value) ? String(value) : value.toLocaleString('pt-BR');
  }

  private posicaoX(indice: number): number {
    const total = this.xAxisLabels().length;
    const util = LARGURA - MARGEM.esquerda - MARGEM.direita;
    if (total < 2) return MARGEM.esquerda + util / 2;
    return MARGEM.esquerda + (util * indice) / (total - 1);
  }

  private posicaoY(value: number): number {
    const { minimo, maximo } = this.limites();
    const util = ALTURA - MARGEM.topo - MARGEM.base;
    if (this.scale() === 'log') {
      const de = Math.log10(minimo);
      const ate = Math.log10(maximo);
      const proporcao = (Math.log10(Math.max(value, minimo)) - de) / (ate - de || 1);
      return ALTURA - MARGEM.base - util * proporcao;
    }
    const proporcao = (value - minimo) / (maximo - minimo || 1);
    return ALTURA - MARGEM.base - util * proporcao;
  }

  private rotuloLog(expoente: number): string {
    return `10^${Math.round(expoente)}`;
  }
}
