import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ChartSeries, LineChartComponent } from './line-chart.component';

// Com oito gases no mesmo quadro, seguir uma linha só com o olho é difícil. Apontar o
// nome dela na legenda apaga as outras, e isso tem que ser medido com o evento
// disparado de verdade: a primeira versão deste caso só conferia a marcação, e
// sobreviveu à mutação que apagava o `mouseenter`.
@Component({
  imports: [LineChartComponent],
  template: `
    <app-line-chart
      title="Evolução"
      [xAxisLabels]="rotulos()"
      [series]="series()"
      [shape]="formato()"
    />
  `,
})
class HospedeiroDaLegenda {
  readonly formato = signal<'wide' | 'compact'>('wide');
  readonly rotulos = signal<readonly string[]>(['01/01/2026', '01/02/2026']);
  readonly series = signal<readonly ChartSeries[]>([
    { key: 'h2', name: 'H₂', palettePosition: 0, values: [10, 20] },
    { key: 'ch4', name: 'CH₄', palettePosition: 1, values: [30, 40] },
  ]);
}

describe('LineChartComponent, legenda', () => {
  function montar() {
    TestBed.configureTestingModule({ imports: [HospedeiroDaLegenda] });
    const fixture = TestBed.createComponent(HospedeiroDaLegenda);

    fixture.detectChanges();

    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  it('anuncia que a lista abaixo do gráfico é a legenda', () => {
    const lista = montar().raiz.querySelector('ul[aria-label]');

    expect(lista?.getAttribute('aria-label')).toBe('Legenda do gráfico');
    expect(lista?.querySelector('li')?.textContent?.trim()).toBe('Legenda:');
  });

  it('apaga as demais séries quando o cursor aponta uma da legenda', () => {
    const { fixture, raiz } = montar();
    const itens = [...raiz.querySelectorAll('ul[aria-label] li')].filter((item) =>
      (item.textContent ?? '').includes('H₂'),
    );
    const linhas = () =>
      [...raiz.querySelectorAll('svg path[stroke-width="2"]')].map((linha) =>
        (linha as HTMLElement).style.opacity,
      );

    expect(linhas().every((opacidade) => opacidade === '1')).toBeTrue();

    itens[0].dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(linhas()).toContain('0.15');
    expect(linhas()).toContain('1');
  });

  // `title` é input e atributo nativo ao mesmo tempo. Com o atributo vivo no DOM, o
  // navegador desenha a tooltip dele por cima da figura, competindo com a leitura do
  // próprio gráfico. Foi visto na tela, na NBR 7274.
  it('não deixa o navegador desenhar a tooltip nativa do título', () => {
    const grafico = montar().raiz.querySelector('app-line-chart');

    expect(grafico?.hasAttribute('title')).toBeFalse();
  });

  // Centrado na borda, metade do rótulo da ponta cai fora do `viewBox` e o SVG corta:
  // a última data da série aparecia como "07/06/20". Ancorar pela borda mantém o texto
  // inteiro dentro do desenho sem alargar a margem.
  it('mantém a primeira e a última data dentro da área desenhada', () => {
    const raiz = montar().raiz;
    const datas = [...raiz.querySelectorAll('svg text')].filter((marca) =>
      /\d{2}\/\d{2}\/\d{4}/.test(marca.textContent ?? ''),
    );

    expect(datas.length).toBe(2);
    expect(datas[0].getAttribute('text-anchor')).toBe('start');
    expect(datas[datas.length - 1].getAttribute('text-anchor')).toBe('end');
  });

  // A proporção larga é para o gráfico que ocupa a linha inteira; dentro de um cartão
  // de grade de três colunas ela achata o desenho, encolhe o texto e sobra branco em
  // volta. Quem escolhe é quem usa, e o padrão continua sendo a larga.
  it('mantém a proporção larga por padrão e aceita a compacta', () => {
    const { fixture, raiz } = montar();

    expect(raiz.querySelector('svg')?.getAttribute('viewBox')).toBe('0 0 720 320');

    fixture.componentInstance.formato.set('compact');
    fixture.detectChanges();

    expect(raiz.querySelector('svg')?.getAttribute('viewBox')).toBe('0 0 480 360');
  });
});

