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
      [xAxisLabels]="rotulos()"
      [series]="series()"
      [scale]="escala()"
    />
  `,
})
class HospedeiroDoGrafico {
  readonly rotulos = signal<readonly string[]>([]);
  readonly series = signal<readonly ChartSeries[]>([]);
  readonly escala = signal<'linear' | 'log'>('log');
}

describe('LineChartComponent, série sem nenhum valor plotável', () => {
  function montar(): HTMLElement {
    TestBed.configureTestingModule({ imports: [HospedeiroDoGrafico] });
    const fixture = TestBed.createComponent(HospedeiroDoGrafico);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('desenha o eixo em escala logarítmica sem derrubar a detecção de mudanças', () => {
    const raiz = montar();

    expect(raiz.querySelector('svg')).not.toBeNull();

    const alturas = [...raiz.querySelectorAll('svg text')]
      .map((marca) => Number(marca.getAttribute('y')))
      .filter((altura) => !Number.isNaN(altura));

    expect(alturas.length).toBeGreaterThan(0);
    expect(alturas.every(Number.isFinite)).toBeTrue();
  });
});
