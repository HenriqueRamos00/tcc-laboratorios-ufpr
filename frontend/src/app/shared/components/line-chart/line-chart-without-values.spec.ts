import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ChartSeries, LineChartComponent } from './line-chart.component';

// Laudo real sem nenhum gás dissolvido existe: LACTEC-02820/2026 traz só dois ensaios
// físico-químicos. A aba de gases desse laudo monta o gráfico com série vazia, e em escala
// logarítmica `Math.log10(0)` é `-Infinity`: a contagem de marcas do eixo virava `Infinity`
// e `Array.from` morria com `RangeError: Invalid array length`.
//
// O erro não ficava no gráfico. Ele acontecia durante a detecção de mudanças, então o ciclo
// inteiro abortava: o `ngDoCheck` do container do Material deixava de rodar, e o menu
// lateral parava de recalcular a margem do conteúdo. Recolher o menu passava a deixar uma
// faixa cinza do tamanho do menu aberto.
@Component({
  imports: [LineChartComponent],
  template: `
    <app-line-chart
      title="Evolução"
      [xAxisLabels]="dates()"
      [series]="series()"
      [scale]="scale()"
    />
  `,
})
class ChartHost {
  readonly dates = signal<readonly string[]>([]);
  readonly series = signal<readonly ChartSeries[]>([]);
  readonly scale = signal<'linear' | 'log'>('log');
}

describe('LineChartComponent, série sem nenhum valor plotável', () => {
  function render(): HTMLElement {
    TestBed.configureTestingModule({ imports: [ChartHost] });
    const fixture = TestBed.createComponent(ChartHost);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('desenha o eixo em escala logarítmica sem derrubar a detecção de mudanças', () => {
    const root = render();

    expect(root.querySelector('svg')).not.toBeNull();

    const heights = [...root.querySelectorAll('svg text')]
      .map((tick) => Number(tick.getAttribute('y')))
      .filter((height) => !Number.isNaN(height));

    expect(heights.length).toBeGreaterThan(0);
    expect(heights.every(Number.isFinite)).toBeTrue();
  });
});
