import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { DuvalPentagonComponent } from '@shared/components/duval-pentagon/duval-pentagon.component';
import { GasRatioTableComponent } from '@shared/components/gas-ratio-table/gas-ratio-table.component';
import { Iec60599FaultCodesComponent } from '@shared/components/iec-60599-fault-codes/iec-60599-fault-codes.component';
import { IeeeC57104ChartComponent } from '@shared/components/ieee-c57104-chart/ieee-c57104-chart.component';
import { Nbr7274ChartComponent } from '@shared/components/nbr-7274-chart/nbr-7274-chart.component';
import { DuvalTriangleComponent } from '@shared/components/duval-triangle/duval-triangle.component';

import { readableDate } from '@shared/brazilian-date';

import {
  NOT_ASSESSED_LABEL,
  type DiagnosisBlock,
  type GasRatioReading,
  type RatioMethodReading,
} from '@/app/model/health';

// Coleta não realizada não é zero nem "não detectado": é lacuna, e o laudo
// impresso marca a lacuna com um hífen.
const NOT_PERFORMED_MARK = '-';


// Geometria das barras da NBR 7274, no sistema de coordenadas do SVG do
// gabarito. As barras nascem da lista de gases que o método acompanha, então o
// passo é calculado e não escrito: gás a mais ou a menos não pode empurrar a
// última barra para fora da figura.

// Geometria da linha do IEEE C57.104, também no sistema do gabarito.
/**
 * Uma razão sem valor mostra o motivo, não uma lacuna: gás sem ensaio e gás não
 * detectado no denominador somem igual se a célula só ficar vazia.
 *
 * A frase mora aqui, perto do rótulo que o cliente lê, e não no modelo. O modelo
 * carrega o desfecho e o gás envolvido; a redação é da tela.
 */
/** Duas casas bastam numa razão de gases, e a vírgula é a do leitor do laudo. */
// Faixas do IEEE C57.104: a severidade é a própria mensagem, então as três
// reusam o trio de feedback do tema em vez de inventar matiz nova.
function ratioText(value: number): string {
  return value.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
}

function percentText(value: number): string {
  return value.toLocaleString('pt-BR', { maximumFractionDigits: 1 });
}

function ratioRows(method: RatioMethodReading | undefined) {
  return (method?.ratios ?? []).map((ratio) => ({
    key: ratio.key,
    label: ratio.label,
    text: ratioOutcomeText(ratio),
    measured: ratio.kind === 'computed',
  }));
}

function ratioOutcomeText(ratio: GasRatioReading): string {
  switch (ratio.kind) {
    case 'computed':
      return ratioText(ratio.value);
    case 'withoutTest':
      return `Sem ensaio de ${ratio.missingGas}`;
    case 'denominatorNotDetected':
      return `${ratio.denominator} não detectado: a razão não existe`;
  }
}





/**
 * A aba de métodos de diagnóstico: as seis figuras que a mesma análise produz.
 *
 * Mora fora da página porque a página não tem nada a dizer sobre elas. A página
 * resolve endereço, aba e busca; aqui é só a leitura do bloco de diagnóstico. O
 * `<section role="tabpanel">` fica na página de propósito, porque o `id` e o
 * `aria-labelledby` dele casam com o botão da aba, que é assunto dela.
 *
 * O bloco entra nulo no tipo, e não opcional: laudo sem gás dissolvido é estado
 * normal, não esquecimento de quem chamou.
 */
@Component({
  selector: 'app-diagnostic-methods',
  standalone: true,
  imports: [
    DuvalTriangleComponent,
    DuvalPentagonComponent,
    GasRatioTableComponent,
    Iec60599FaultCodesComponent,
    IeeeC57104ChartComponent,
    Nbr7274ChartComponent,
  ],
  templateUrl: './diagnostic-methods.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class DiagnosticMethodsComponent {
  readonly diagnosis = input.required<DiagnosisBlock | null>();

  protected readonly notAssessedLabel = NOT_ASSESSED_LABEL;


  /** Percentual de cada gás do pentágono, já no formato que o cliente lê. */
  protected readonly pentagonPercentages = computed(() =>
    (this.diagnosis()?.pentagon.percentages ?? []).map((item) => ({
      gas: item.gas,
      text: item.percentage === null ? NOT_PERFORMED_MARK : `${percentText(item.percentage)} %`,
    })),
  );

  protected readonly iecRatios = computed(() => ratioRows(this.diagnosis()?.iec60599));

  protected readonly rogersRatios = computed(() => ratioRows(this.diagnosis()?.rogers));

  // Escala e passo saem de um derivado só: traçado, marcadores e linhas de
  // referência precisam dividir o mesmo eixo, senão a linha e o limiar ficam em
  // alturas que não se comparam.






  // A legenda nasce do que a figura de fato desenha: com limite não avaliado,
  // nenhuma barra ganha linha de limite, e anunciar a marca na legenda faria a
  // legenda prometer um traço que não existe.
}
