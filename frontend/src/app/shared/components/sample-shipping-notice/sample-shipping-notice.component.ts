import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { LABORATORY_ADDRESS, type ShippingGuidance } from '@/app/model/sample-shipping';

@Component({
  selector: 'app-sample-shipping-notice',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './sample-shipping-notice.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SampleShippingNoticeComponent {
  /** Código que o cliente escreve na etiqueta para o laboratório achar o orçamento. */
  readonly proposalCode = input.required<string>();
  readonly guidance = input<ShippingGuidance>('labeling');

  protected readonly address = LABORATORY_ADDRESS;
  protected readonly showsAddress = computed(() => this.guidance() === 'address-and-labeling');
}
