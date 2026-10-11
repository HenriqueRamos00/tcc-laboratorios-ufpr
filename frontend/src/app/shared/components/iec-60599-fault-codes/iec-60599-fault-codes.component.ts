import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import type { FaultCode } from '@/app/model/health';

/**
 * A legenda dos códigos de falha, para quem lê o laudo saber o que cada sigla
 * quer dizer.
 *
 * É legenda, e não diagnóstico: ela lista os códigos possíveis e o que cada um
 * significa, sem afirmar qual deles é o deste laudo. Atribuir o código exige a
 * tabela de faixas da norma, que não está neste projeto.
 */
@Component({
  selector: 'app-iec-60599-fault-codes',
  standalone: true,
  templateUrl: './iec-60599-fault-codes.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class Iec60599FaultCodesComponent {
  readonly codes = input.required<readonly FaultCode[]>();
}
