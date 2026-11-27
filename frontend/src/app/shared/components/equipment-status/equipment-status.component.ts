import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
import {
  STATUS_PILL_SLOT_CLASS,
  StatusPillButtonComponent,
  StatusPillComponent,
  type StatusPillTone,
} from '@shared/components/status-pill/status-pill.component';

import { EQUIPMENT_STATUS_LABEL, type EquipmentStatus } from '@/app/model/equipment';

@Component({
  selector: 'app-equipment-status',
  standalone: true,
  imports: [StatusPillComponent, StatusPillButtonComponent, DataUnavailableComponent],
  host: { class: STATUS_PILL_SLOT_CLASS },
  template: `
    @if (status(); as current) {
      <app-status-pill [tone]="tone()" [label]="label[current.overall]" />
    } @else if (refreshable()) {
      <app-status-pill-button
        [label]="failed() ? 'Tentar de novo' : 'Atualizar'"
        loadingLabel="Atualizando"
        [loading]="refreshing()"
        [description]="'Atualizar a situação de ' + subject()"
        (pressed)="refresh.emit()"
      />
      @if (failed() && !refreshing()) {
        <span role="alert" class="sr-only">Não foi possível atualizar a situação de {{ subject() }}.</span>
      }
    } @else {
      <span class="whitespace-nowrap"><app-data-unavailable subject="Situação" /></span>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentStatusComponent {
  readonly status = input.required<EquipmentStatus | null>();
  readonly subject = input.required<string>();
  readonly refreshing = input(false);
  readonly failed = input(false);
  readonly refreshable = input(true);

  readonly refresh = output<void>();

  protected readonly label = EQUIPMENT_STATUS_LABEL;

  protected readonly tone = computed<StatusPillTone>(() => {
    const overall = this.status()?.overall;
    return overall === 'normal' || overall === 'alerta' || overall === 'nao-conforme' ? overall : 'neutro';
  });
}
