import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

// A escala logarítmica existe porque a cromatografia tem o nitrogênio quatro
// ordens de grandeza acima do acetileno e some numa escala linear.
//
// Identidade nunca vem só da cor: cada série tem uma forma de marcador,
// repetida na legenda, e o tooltip do cruzamento mostra todas de uma vez.

export interface SerieDoGrafico {
  readonly chave: string;
  readonly nome: string;
  /** Índice na paleta categórica; sem ciclo automático. */
  readonly cor: number;
  readonly valores: readonly (number | null)[];
}

export type EscalaDoEixo = 'linear' | 'log';

// Paleta categórica validada para superfície clara (checagem de CVD, faixa de
// luminosidade e piso de visão normal). A ordem é fixa.
const PALETA = [
  '#2a78d6',
  '#eb6834',
  '#1baf7a',
  '#eda100',
  '#e87ba4',
  '#008300',
  '#4a3aa7',
  '#e34948',
] as const;

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
  readonly chave: string;
  readonly nome: string;
  readonly cor: string;
  readonly forma: Forma;
  readonly tracejada: boolean;
  readonly caminho: string;
  readonly pontos: readonly PontoDesenhado[];
}

@Component({
  selector: 'app-line-chart',
  standalone: true,
  host: { class: 'block' },
  templateUrl: './line-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineChartComponent {
  readonly titulo = input.required<string>();
  readonly rotulosDoEixoX = input.required<readonly string[]>();
  readonly series = input.required<readonly SerieDoGrafico[]>();
  readonly escala = input<EscalaDoEixo>('linear');
  readonly rotuloDoEixoY = input('Valor');
  readonly rotuloDoEixoX = input('Data');

  protected readonly LARGURA = LARGURA;
  protected readonly ALTURA = ALTURA;
  protected readonly MARGEM = MARGEM;

  protected readonly indiceEmFoco = signal<number | null>(null);

  private readonly limites = computed(() => {
    const valores = this.series()
      .flatMap((serie) => serie.valores)
      .filter(
        (valor): valor is number =>
          valor !== null &&
          Number.isFinite(valor) &&
          (this.escala() === 'linear' || valor > 0),
      );

    if (!valores.length) return { minimo: 0, maximo: 1 };

    const bruto = { minimo: Math.min(...valores), maximo: Math.max(...valores) };
    if (this.escala() === 'log') {
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
    if (this.escala() === 'log') {
      const de = Math.log10(minimo);
      const ate = Math.log10(maximo);
      return Array.from({ length: Math.round(ate - de) + 1 }, (_, passo) => {
        const valor = Math.pow(10, de + passo);
        return { valor, y: this.posicaoY(valor), rotulo: this.rotuloLog(de + passo) };
      });
    }
    return Array.from({ length: 6 }, (_, marca) => {
      const valor = minimo + ((maximo - minimo) * marca) / 5;
      return { valor, y: this.posicaoY(valor), rotulo: this.formatarNumero(valor) };
    });
  });

  protected readonly marcasDoEixoX = computed(() =>
    this.rotulosDoEixoX().map((rotulo, indice) => ({ rotulo, x: this.posicaoX(indice) })),
  );

  protected readonly seriesDesenhadas = computed<readonly SerieDesenhada[]>(() =>
    this.series().map((serie, indice) => {
      const pontos: PontoDesenhado[] = [];
      const trechos: string[] = [];
      let abrindoTrecho = true;

      serie.valores.forEach((valor, posicao) => {
        if (valor === null || (this.escala() === 'log' && valor <= 0)) {
          abrindoTrecho = true;
          return;
        }
        const ponto = { x: this.posicaoX(posicao), y: this.posicaoY(valor) };
        pontos.push(ponto);
        trechos.push(`${abrindoTrecho ? 'M' : 'L'} ${ponto.x} ${ponto.y}`);
        abrindoTrecho = false;
      });

      return {
        chave: serie.chave,
        nome: serie.nome,
        cor: PALETA[serie.cor % PALETA.length],
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
      rotulo: this.rotulosDoEixoX()[indice],
      valores: this.series().map((serie, ordem) => ({
        nome: serie.nome,
        cor: PALETA[serie.cor % PALETA.length],
        forma: FORMAS[Math.floor(ordem / PALETA.length) % FORMAS.length],
        valor: serie.valores[indice],
      })),
    };
  });

  protected aoMoverPonteiro(evento: MouseEvent): void {
    const alvo = evento.currentTarget as SVGGraphicsElement;
    const caixa = alvo.getBoundingClientRect();
    if (!caixa.width) return;
    const proporcao = (evento.clientX - caixa.left) / caixa.width;
    const dentro = proporcao * LARGURA;
    const total = this.rotulosDoEixoX().length;
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

  protected formatarNumero(valor: number | null): string {
    if (valor === null) return 'ND';
    if (Math.abs(valor) >= 1000) return valor.toLocaleString('pt-BR');
    return Number.isInteger(valor) ? String(valor) : valor.toLocaleString('pt-BR');
  }

  private posicaoX(indice: number): number {
    const total = this.rotulosDoEixoX().length;
    const util = LARGURA - MARGEM.esquerda - MARGEM.direita;
    if (total < 2) return MARGEM.esquerda + util / 2;
    return MARGEM.esquerda + (util * indice) / (total - 1);
  }

  private posicaoY(valor: number): number {
    const { minimo, maximo } = this.limites();
    const util = ALTURA - MARGEM.topo - MARGEM.base;
    if (this.escala() === 'log') {
      const de = Math.log10(minimo);
      const ate = Math.log10(maximo);
      const proporcao = (Math.log10(Math.max(valor, minimo)) - de) / (ate - de || 1);
      return ALTURA - MARGEM.base - util * proporcao;
    }
    const proporcao = (valor - minimo) / (maximo - minimo || 1);
    return ALTURA - MARGEM.base - util * proporcao;
  }

  private rotuloLog(expoente: number): string {
    return `10^${Math.round(expoente)}`;
  }
}
