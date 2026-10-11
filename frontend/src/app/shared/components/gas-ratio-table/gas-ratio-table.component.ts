import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { NOT_ASSESSED_LABEL } from '@/app/model/health';

/**
 * Uma linha da tabela, já com a frase pronta. A redação mora na tela que monta as
 * linhas, perto do rótulo que o cliente lê, e não no modelo.
 */
export interface GasRatioRow {
  readonly key: string;
  readonly label: string;
  readonly text: string;
  readonly measured: boolean;
}

/**
 * As razões características que um método lê, com o motivo escrito quando a razão
 * não existe.
 *
 * Serve a IEC 60599 e Rogers, que diferem nas razões que leem e não na forma de
 * apresentar. Não existe figura de Rogers aqui de propósito: a grade gás por gás
 * pintada de termico ou descarga que esta tela já exibiu não é o método de Rogers
 * em versao nenhuma, e publicar uma figura inventada com nome de norma é pior que
 * nao publicar figura.
 *
 * O código de falha depende da tabela de faixas de cada norma, que não está neste
 * projeto. Sem a tabela a razão sai medida e o código sai declarado como não
 * avaliado, com o motivo.
 */
@Component({
  selector: 'app-gas-ratio-table',
  standalone: true,
  templateUrl: './gas-ratio-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class GasRatioTableComponent {
  readonly title = input.required<string>();
  readonly legend = input.required<string>();
  readonly rows = input.required<readonly GasRatioRow[]>();
  readonly faultCode = input.required<string | null>();
  readonly faultCodeNotAssessed = input.required<string | null>();

  protected readonly notAssessedLabel = NOT_ASSESSED_LABEL;
}
