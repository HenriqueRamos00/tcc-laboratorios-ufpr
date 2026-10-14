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

const MARCAS_DO_EIXO_LINEAR = 5;

// Duas proporções, e a escolha é de quem usa, não do componente. A faixa larga é
// para o gráfico que ocupa a linha inteira da tela; a compacta é para o cartão de uma
// grade de três colunas, onde a proporção larga deixa o desenho achatado, o texto
// miúdo e sobra branco em volta.
const FORMATOS = {
  wide: { largura: 720, altura: 320 },
  compact: { largura: 480, altura: 360 },
} as const;

export type ChartShape = keyof typeof FORMATOS;
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

  protected readonly LARGURA = computed(() => FORMATOS[this.shape()].largura);
  protected readonly ALTURA = computed(() => FORMATOS[this.shape()].altura);
  protected readonly MARGEM = MARGEM;

  protected readonly indiceEmFoco = signal<number | null>(null);

  /**
   * Qual série a legenda está destacando. Com oito gases no mesmo quadro, seguir uma
   * linha só com o olho é difícil; apontar o nome dela na legenda apaga as outras e
   * resolve sem exigir clique nem filtro.
   */
  protected readonly serieEmFoco = signal<string | null>(null);

  protected opacidadeDaSerie(key: string): number {
    const emFoco = this.serieEmFoco();

    return emFoco === null || emFoco === key ? 1 : 0.15;
  }

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
    // Escala linear fecha em passo redondo, como uma biblioteca de gráfico faz. Fatiar
    // a faixa medida em cinco partes iguais produzia marca como 270,8 e 309,68: largas
    // demais para o eixo e, em português, confundidas com decimal por causa do ponto de
    // milhar. A base não é forçada a zero porque aqui a figura é evolução, e uma
    // variação pequena colada no topo esconde justamente o que se quer ver.
    const eixo = eixoDeValores(bruto.minimo, bruto.maximo, MARCAS_DO_EIXO_LINEAR);

    return { minimo: eixo.minimo, maximo: eixo.maximo, marcas: eixo.marcas };
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
        return { value, y: this.posicaoY(value), label: this.rotuloLog(de + passo) };
      });
    }
    const marcas = this.limites().marcas ?? [];

    return marcas.map((value) => ({ value, y: this.posicaoY(value), label: rotuloDoEixo(value) }));
  });

  /**
   * A primeira e a última data ancoram pela borda, e não pelo centro.
   *
   * Centrado, metade do rótulo da ponta fica fora da área de plotagem e o `viewBox`
   * corta: a última data da série aparecia como "07/06/20". Alargar a margem resolveria
   * também, mas rouba largura do desenho nas duas pontas por causa de dois rótulos.
   */
  protected readonly marcasDoEixoX = computed(() => {
    const rotulos = this.xAxisLabels();

    return rotulos.map((label, index) => ({
      label,
      x: this.posicaoX(index),
      ancora: index === 0 ? 'start' : index === rotulos.length - 1 ? 'end' : 'middle',
    }));
  });

  protected readonly seriesDesenhadas = computed<readonly SerieDesenhada[]>(() =>
    this.series().map((serie, index) => {
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
        forma: FORMAS[Math.floor(index / PALETA.length) % FORMAS.length],
        // A partir da nona série a cor se repete; o traço passa a tracejado
        // para que a identidade continue distinguível sem inventar matiz nova.
        tracejada: index >= PALETA.length,
        caminho: trechos.join(' '),
        pontos,
      };
    }),
  );

  protected readonly leituraEmFoco = computed(() => {
    const index = this.indiceEmFoco();
    if (index === null) return null;
    return {
      x: this.posicaoX(index),
      label: this.xAxisLabels()[index],
      values: this.series().map((serie, ordem) => ({
        name: serie.name,
        classes: PALETA[serie.palettePosition % PALETA.length],
        forma: FORMAS[Math.floor(ordem / PALETA.length) % FORMAS.length],
        value: serie.values[index],
      })),
    };
  });

  protected aoMoverPonteiro(evento: MouseEvent): void {
    const alvo = evento.currentTarget as SVGGraphicsElement;
    const caixa = alvo.getBoundingClientRect();
    if (!caixa.width) return;
    const proporcao = (evento.clientX - caixa.left) / caixa.width;
    const dentro = proporcao * this.LARGURA();
    const total = this.xAxisLabels().length;
    if (total < 2) {
      this.indiceEmFoco.set(total === 1 ? 0 : null);
      return;
    }
    const passo = (this.LARGURA() - MARGEM.esquerda - MARGEM.direita) / (total - 1);
    const index = Math.round((dentro - MARGEM.esquerda) / passo);
    this.indiceEmFoco.set(Math.min(Math.max(index, 0), total - 1));
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

  private posicaoX(index: number): number {
    const total = this.xAxisLabels().length;
    const util = this.LARGURA() - MARGEM.esquerda - MARGEM.direita;
    if (total < 2) return MARGEM.esquerda + util / 2;
    return MARGEM.esquerda + (util * index) / (total - 1);
  }

  private posicaoY(value: number): number {
    const { minimo, maximo } = this.limites();
    const util = this.ALTURA() - MARGEM.topo - MARGEM.base;
    if (this.scale() === 'log') {
      const de = Math.log10(minimo);
      const ate = Math.log10(maximo);
      const proporcao = (Math.log10(Math.max(value, minimo)) - de) / (ate - de || 1);
      return this.ALTURA() - MARGEM.base - util * proporcao;
    }
    const proporcao = (value - minimo) / (maximo - minimo || 1);
    return this.ALTURA() - MARGEM.base - util * proporcao;
  }

  private rotuloLog(expoente: number): string {
    return `10^${Math.round(expoente)}`;
  }
}
