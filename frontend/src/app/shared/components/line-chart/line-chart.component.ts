import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { eixoDeValores, rotuloDoEixo } from '../eixo-de-valores';

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
interface SeriesClasses {
  readonly stroke: string;
  readonly marker: string;
}

const PALETTE: readonly SeriesClasses[] = [
  { stroke: 'stroke-lactec-serie-primeira', marker: 'fill-lactec-serie-primeira' },
  { stroke: 'stroke-lactec-serie-segunda', marker: 'fill-lactec-serie-segunda' },
  { stroke: 'stroke-lactec-serie-terceira', marker: 'fill-lactec-serie-terceira' },
  { stroke: 'stroke-lactec-serie-quarta', marker: 'fill-lactec-serie-quarta' },
  { stroke: 'stroke-lactec-serie-quinta', marker: 'fill-lactec-serie-quinta' },
  { stroke: 'stroke-lactec-serie-sexta', marker: 'fill-lactec-serie-sexta' },
  { stroke: 'stroke-lactec-serie-setima', marker: 'fill-lactec-serie-setima' },
  { stroke: 'stroke-lactec-serie-oitava', marker: 'fill-lactec-serie-oitava' },
];

const MARKER_SHAPES = ['circle', 'square', 'triangle', 'diamond'] as const;
type MarkerShape = (typeof MARKER_SHAPES)[number];

const LINEAR_AXIS_TICKS = 5;

// Duas colunas na leitura existem para a fórmula do gás ("C₂H₂"): nove séries
// empilhadas numa coluna só fazem uma caixa mais alta do que o próprio gráfico.
// O ensaio físico-químico chega com a unidade dentro do nome ("Índice de
// Neutralização (mg KOH/g óleo)") e, repartido em duas colunas, o nome da
// esquerda era desenhado por cima do nome da direita. Dezesseis caracteres é o
// que cabe em meia caixa com folga para o marcador e para o valor.
const LONGEST_NAME_IN_TWO_COLUMNS = 16;
const SERIES_THAT_FILL_TWO_COLUMNS = 4;

// Duas proporções, e a escolha é de quem usa, não do componente. A faixa larga é
// para o gráfico que ocupa a linha inteira da tela; a compacta é para o cartão de uma
// grade de três colunas, onde a proporção larga deixa o desenho achatado, o texto
// miúdo e sobra branco em volta.
const SIZES = {
  wide: { width: 720, height: 320 },
  compact: { width: 480, height: 360 },
} as const;

export type ChartShape = keyof typeof SIZES;
const MARGIN = { top: 16, right: 16, bottom: 46, left: 56 };

interface PlottedPoint {
  readonly x: number;
  readonly y: number;
}

interface PlottedSeries {
  readonly key: string;
  readonly name: string;
  readonly classes: SeriesClasses;
  readonly shape: MarkerShape;
  readonly dashed: boolean;
  readonly path: string;
  readonly points: readonly PlottedPoint[];
}

// Lacuna de coleta nao e "nao detectado": o ensaio nao foi feito naquela data.
// A tabela de indicadores marca a mesma ausencia do mesmo jeito.
const MARK_FOR_COLLECTION_NOT_PERFORMED = '-';

@Component({
  selector: 'app-line-chart',
  standalone: true,
  // `title` é input deste componente e também atributo nativo do HTML. Escrever
  // `title="..."` na tag alimenta o input E deixa o atributo no DOM, e aí o navegador
  // desenha a própria tooltip dele por cima da figura, competindo com a leitura que o
  // gráfico mostra ao passar o cursor. Apagar o atributo no host resolve sem mexer no
  // nome do input, que já é consumido por várias telas.
  host: { class: 'block', '[attr.title]': 'null' },
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

  readonly shape = input<ChartShape>('wide');

  protected readonly chartWidth = computed(() => SIZES[this.shape()].width);
  protected readonly chartHeight = computed(() => SIZES[this.shape()].height);
  protected readonly MARGIN = MARGIN;

  protected readonly pointedIndex = signal<number | null>(null);

  /**
   * Qual série a legenda está destacando. Com oito gases no mesmo quadro, seguir uma
   * linha só com o olho é difícil; apontar o nome dela na legenda apaga as outras e
   * resolve sem exigir clique nem filtro.
   */
  protected readonly highlightedSeries = signal<string | null>(null);

  protected seriesOpacity(key: string): number {
    const highlighted = this.highlightedSeries();

    return highlighted === null || highlighted === key ? 1 : 0.15;
  }

  /** A leitura só se divide em duas colunas quando todo nome cabe em meia caixa. */
  protected readonly readingInTwoColumns = computed(() => {
    const names = this.series().map((serie) => serie.name);

    return (
      names.length >= SERIES_THAT_FILL_TWO_COLUMNS &&
      names.every((name) => name.length <= LONGEST_NAME_IN_TWO_COLUMNS)
    );
  });

  private readonly bounds = computed(() => {
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
      return this.scale() === 'log' ? { minimum: 1, maximum: 10 } : { minimum: 0, maximum: 1 };
    }

    const measured = { minimum: Math.min(...values), maximum: Math.max(...values) };
    if (this.scale() === 'log') {
      return {
        minimum: Math.pow(10, Math.floor(Math.log10(measured.minimum))),
        maximum: Math.pow(10, Math.ceil(Math.log10(measured.maximum))),
      };
    }
    // Escala linear fecha em passo redondo, como uma biblioteca de gráfico faz. Fatiar
    // a faixa medida em cinco partes iguais produzia marca como 270,8 e 309,68: largas
    // demais para o eixo e, em português, confundidas com decimal por causa do ponto de
    // milhar. A base não é forçada a zero porque aqui a figura é evolução, e uma
    // variação pequena colada no topo esconde justamente o que se quer ver.
    const axis = eixoDeValores(measured.minimum, measured.maximum, LINEAR_AXIS_TICKS);

    return { minimum: axis.minimo, maximum: axis.maximo, ticks: axis.marcas };
  });

  protected readonly yAxisTicks = computed(() => {
    const { minimum, maximum } = this.bounds();
    if (this.scale() === 'log') {
      const from = Math.log10(minimum);
      const to = Math.log10(maximum);
      // Cinto além da trava acima: nenhuma faixa de entrada pode virar um número de marcas
      // que `Array.from` recuse. Uma década por marca, no mínimo duas e no máximo dez.
      const decades = Math.round(to - from);
      const howMany = Number.isFinite(decades) ? Math.min(Math.max(decades, 1), 9) + 1 : 2;
      return Array.from({ length: howMany }, (_, step) => {
        const value = Math.pow(10, from + step);
        return { value, y: this.positionY(value), label: this.logTickLabel(from + step) };
      });
    }
    const ticks = this.bounds().ticks ?? [];

    return ticks.map((value) => ({ value, y: this.positionY(value), label: rotuloDoEixo(value) }));
  });

  /**
   * A primeira e a última data ancoram pela borda, e não pelo centro.
   *
   * Centrado, metade do rótulo da ponta fica fora da área de plotagem e o `viewBox`
   * corta: a última data da série aparecia como "07/06/20". Alargar a margem resolveria
   * também, mas rouba largura do desenho nas duas pontas por causa de dois rótulos.
   */
  protected readonly xAxisTicks = computed(() => {
    const labels = this.xAxisLabels();

    return labels.map((label, index) => ({
      label,
      x: this.positionX(index),
      anchor: index === 0 ? 'start' : index === labels.length - 1 ? 'end' : 'middle',
    }));
  });

  protected readonly plottedSeries = computed<readonly PlottedSeries[]>(() =>
    this.series().map((serie, index) => {
      const points: PlottedPoint[] = [];
      const commands: string[] = [];
      let startingStretch = true;

      serie.values.forEach((value, position) => {
        if (value === null || (this.scale() === 'log' && value <= 0)) {
          startingStretch = true;
          return;
        }
        const point = { x: this.positionX(position), y: this.positionY(value) };
        points.push(point);
        commands.push(`${startingStretch ? 'M' : 'L'} ${point.x} ${point.y}`);
        startingStretch = false;
      });

      return {
        key: serie.key,
        name: serie.name,
        classes: PALETTE[serie.palettePosition % PALETTE.length],
        shape: MARKER_SHAPES[Math.floor(index / PALETTE.length) % MARKER_SHAPES.length],
        // A partir da nona série a cor se repete; o traço passa a tracejado
        // para que a identidade continue distinguível sem inventar matiz nova.
        dashed: index >= PALETTE.length,
        path: commands.join(' '),
        points,
      };
    }),
  );

  protected readonly pointedReading = computed(() => {
    const index = this.pointedIndex();
    if (index === null) return null;
    return {
      x: this.positionX(index),
      label: this.xAxisLabels()[index],
      values: this.series().map((serie, order) => ({
        name: serie.name,
        classes: PALETTE[serie.palettePosition % PALETTE.length],
        shape: MARKER_SHAPES[Math.floor(order / PALETTE.length) % MARKER_SHAPES.length],
        value: serie.values[index],
      })),
    };
  });

  protected onPointerMove(event: MouseEvent): void {
    const chart = event.currentTarget as SVGGraphicsElement;
    const area = chart.getBoundingClientRect();
    if (!area.width) return;
    const proportion = (event.clientX - area.left) / area.width;
    const inside = proportion * this.chartWidth();
    const total = this.xAxisLabels().length;
    if (total < 2) {
      this.pointedIndex.set(total === 1 ? 0 : null);
      return;
    }
    const step = (this.chartWidth() - MARGIN.left - MARGIN.right) / (total - 1);
    const index = Math.round((inside - MARGIN.left) / step);
    this.pointedIndex.set(Math.min(Math.max(index, 0), total - 1));
  }

  protected onPointerLeave(): void {
    this.pointedIndex.set(null);
  }

  protected markerPath(shape: MarkerShape, x: number, y: number): string {
    const radius = 4.5;
    switch (shape) {
      case 'square':
        return `M ${x - radius} ${y - radius} H ${x + radius} V ${y + radius} H ${x - radius} Z`;
      case 'triangle':
        return `M ${x} ${y - radius - 1} L ${x + radius + 1} ${y + radius} L ${x - radius - 1} ${y + radius} Z`;
      case 'diamond':
        return `M ${x} ${y - radius - 1} L ${x + radius + 1} ${y} L ${x} ${y + radius + 1} L ${x - radius - 1} ${y} Z`;
      default:
        return `M ${x - radius} ${y} a ${radius} ${radius} 0 1 0 ${radius * 2} 0 a ${radius} ${radius} 0 1 0 ${-radius * 2} 0`;
    }
  }

  protected formatNumber(value: number | null): string {
    if (value === null) return MARK_FOR_COLLECTION_NOT_PERFORMED;
    if (Math.abs(value) >= 1000) return value.toLocaleString('pt-BR');
    return Number.isInteger(value) ? String(value) : value.toLocaleString('pt-BR');
  }

  private positionX(index: number): number {
    const total = this.xAxisLabels().length;
    const usable = this.chartWidth() - MARGIN.left - MARGIN.right;
    if (total < 2) return MARGIN.left + usable / 2;
    return MARGIN.left + (usable * index) / (total - 1);
  }

  private positionY(value: number): number {
    const { minimum, maximum } = this.bounds();
    const usable = this.chartHeight() - MARGIN.top - MARGIN.bottom;
    if (this.scale() === 'log') {
      const from = Math.log10(minimum);
      const to = Math.log10(maximum);
      const proportion = (Math.log10(Math.max(value, minimum)) - from) / (to - from || 1);
      return this.chartHeight() - MARGIN.bottom - usable * proportion;
    }
    const proportion = (value - minimum) / (maximum - minimum || 1);
    return this.chartHeight() - MARGIN.bottom - usable * proportion;
  }

  private logTickLabel(exponent: number): string {
    return `10^${Math.round(exponent)}`;
  }
}
