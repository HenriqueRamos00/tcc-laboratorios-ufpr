import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { BarChartComponent, type BarReading } from '@shared/components/bar-chart/bar-chart.component';

import { NOT_ASSESSED_LABEL, type Nbr7274Block } from '@/app/model/health';

/**
 * Concentração de cada gás de falha na coleta mais recente.
 *
 * Desenha com o gráfico de barras compartilhado, na mesma linguagem do gráfico de
 * linha da tela: mesma grade, mesmo eixo e leitura ao passar o cursor. Antes era um
 * SVG próprio, sem resposta ao cursor e com canto arredondado, que arredonda também
 * o topo da barra, que é justamente onde o valor é lido.
 *
 * O limite por gás é da norma e não está no projeto. Sem limite não há reta de
 * comparação, e a legenda não anuncia uma marca que não existe.
 */
@Component({
  selector: 'app-nbr-7274-chart',
  standalone: true,
  imports: [BarChartComponent],
  templateUrl: './nbr-7274-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class Nbr7274ChartComponent {
  readonly block = input.required<Nbr7274Block>();

  protected readonly notAssessedLabel = NOT_ASSESSED_LABEL;

  protected readonly readings = computed<readonly BarReading[]>(() =>
    this.block().readings.map((leitura) => ({
      key: leitura.gas,
      label: leitura.gas,
      value: leitura.value,
    })),
  );
}
