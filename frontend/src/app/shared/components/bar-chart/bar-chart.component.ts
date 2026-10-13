import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { eixoDeValores, rotuloDoEixo } from '../eixo-de-valores';

/** Uma barra: a categoria do eixo e o valor medido, ou nulo quando não há medição. */
export interface BarReading {
  readonly key: string;
  readonly label: string;
  readonly value: number | null;
}

// Proporção mais quadrada que a do gráfico de linha, porque barra lado a lado fica
// achatada num quadro largo. A margem esquerda acomoda o rótulo do eixo de valor.
const LARGURA = 480;
const ALTURA = 360;
const MARGEM = { topo: 16, direita: 16, base: 46, esquerda: 56 };

const MARCAS_DO_EIXO = 5;

/**
 * Gráfico de barras por categoria, na mesma linguagem do gráfico de linha da tela:
 * mesma grade recessiva, mesmo eixo e leitura ao passar o cursor.
 *
 * Canto reto de propósito. Barra arredondada arredonda também o topo, que é
 * justamente onde o valor é lido, e desloca a leitura para baixo do que foi medido.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  templateUrl: './bar-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `title` é input deste componente e também atributo nativo do HTML. Escrever
  // `title="..."` na tag alimenta o input E deixa o atributo no DOM, e aí o navegador
  // desenha a própria tooltip dele por cima da figura, competindo com a leitura que o
  // gráfico mostra ao passar o cursor. Apagar o atributo no host resolve sem mexer no
  // nome do input, que já é consumido por várias telas.
  host: { class: 'block', '[attr.title]': 'null' },
})
export class BarChartComponent {
  readonly title = input.required<string>();
  readonly readings = input.required<readonly BarReading[]>();
  readonly yAxisLabel = input('Valor');
  readonly unit = input('');

  protected readonly LARGURA = LARGURA;
  protected readonly ALTURA = ALTURA;
  protected readonly MARGEM = MARGEM;

  protected readonly indiceEmFoco = signal<number | null>(null);

  /**
   * Sem nenhuma medição não há escala, e dividir por ela devolveria `NaN`. O SVG
   * descarta elemento com `NaN` sem acusar erro, e a figura sumiria em silêncio.
   */
  protected readonly eixo = computed(() => {
    const medidos = this.readings()
      .map((leitura) => leitura.value)
      .filter((valor): valor is number => valor !== null && Number.isFinite(valor));

    // Barra nasce do zero: altura proporcional ao valor é o que a figura promete.
    return eixoDeValores(0, medidos.length ? Math.max(...medidos) : 0, MARCAS_DO_EIXO);
  });

  protected readonly maximo = computed(() => this.eixo().maximo);

  protected readonly marcasDoEixoY = computed(() =>
    this.eixo().marcas.map((valor) => ({
      value: valor,
      y: this.posicaoY(valor),
      label: rotuloDoEixo(valor),
    })),
  );

  protected readonly barras = computed(() => {
    const leituras = this.readings();
    const passo = (LARGURA - MARGEM.esquerda - MARGEM.direita) / Math.max(1, leituras.length);
    const largura = Math.min(44, passo * 0.6);

    return leituras.map((leitura, posicao) => {
      const centro = MARGEM.esquerda + passo * (posicao + 0.5);
      const topo = leitura.value === null ? MARGEM.topo : this.posicaoY(leitura.value);

      return {
        key: leitura.key,
        label: leitura.label,
        value: leitura.value,
        texto: leitura.value === null ? 'sem ensaio' : `${rotuloDoEixo(leitura.value)} ${this.unit()}`.trim(),
        x: centro - largura / 2,
        centro,
        largura,
        y: topo,
        altura: leitura.value === null ? 0 : ALTURA - MARGEM.base - topo,
      };
    });
  });

  protected readonly leituraEmFoco = computed(() => {
    const index = this.indiceEmFoco();

    return index === null ? null : (this.barras()[index] ?? null);
  });

  protected aoMoverPonteiro(evento: MouseEvent): void {
    const alvo = evento.currentTarget as SVGGraphicsElement;
    const caixa = alvo.getBoundingClientRect();

    if (!caixa.width) return;

    const total = this.readings().length;

    if (!total) return;

    const dentro = ((evento.clientX - caixa.left) / caixa.width) * LARGURA;
    const passo = (LARGURA - MARGEM.esquerda - MARGEM.direita) / total;
    const index = Math.floor((dentro - MARGEM.esquerda) / passo);

    this.indiceEmFoco.set(index >= 0 && index < total ? index : null);
  }

  protected aoSairDoGrafico(): void {
    this.indiceEmFoco.set(null);
  }

  private posicaoY(valor: number): number {
    const altura = ALTURA - MARGEM.topo - MARGEM.base;

    return ALTURA - MARGEM.base - (valor / this.maximo()) * altura;
  }

}
