import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { readableDate } from '@shared/brazilian-date';
import { LineChartComponent, type ChartSeries } from '@shared/components/line-chart/line-chart.component';

import { NOT_ASSESSED_LABEL, type IeeeBlock } from '@/app/model/health';

/**
 * A evolução do total de gases combustíveis, coleta a coleta.
 *
 * Desenha com o mesmo gráfico da aba de critérios, em vez de um SVG próprio: é a
 * mesma figura, uma série ao longo do tempo, e a tela já tinha um componente que
 * faz isso com eixo, leitura ao passar o cursor e enquadramento que não corta o
 * último ponto. Dois desenhos para a mesma coisa divergem com o tempo, e foi o que
 * aconteceu aqui: o gráfico à mão cortava a figura e não respondia ao cursor.
 *
 * Os limiares de atenção e de alarme são da norma e dependem do tipo e da idade do
 * equipamento, que o AutoLAB não cadastra. Enquanto não existirem, a figura mostra a
 * evolução medida e diz que as faixas não foram avaliadas, em vez de desenhar reta
 * em altura inventada.
 */
@Component({
  selector: 'app-ieee-c57104-chart',
  standalone: true,
  imports: [LineChartComponent],
  templateUrl: './ieee-c57104-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class IeeeC57104ChartComponent {
  readonly block = input.required<IeeeBlock>();

  protected readonly notAssessedLabel = NOT_ASSESSED_LABEL;

  protected readonly dates = computed(() =>
    this.block().readings.map((reading) => readableDate(reading.date)),
  );

  protected readonly series = computed<readonly ChartSeries[]>(() => [
    {
      key: 'combustiveis',
      name: 'Gases combustíveis',
      palettePosition: 0,
      values: this.block().readings.map((reading) => reading.total),
    },
  ]);

  /** O total da coleta mais recente, ou o motivo de não haver total comparável. */
  protected readonly combustibleGasSummary = computed(() => {
    const latest = this.block().readings.at(-1);

    if (!latest) return null;
    if (latest.total === null) return latest.notComputed;

    return `${readableDate(latest.date)}: ${latest.total.toLocaleString('pt-BR')} ppm`;
  });
}
