import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

@Component({
  selector: 'app-unavailable-feature',
  standalone: true,
  imports: [EmptyStateComponent, PageHeaderComponent],
  template: `
    <app-page-header
      [titulo]="titulo()"
      subtitulo="A página solicitada ainda não está disponível."
      [migalhas]="[{ rotulo: 'Orçamentos', rota: '/cliente/orcamentos' }, { rotulo: titulo() }]"
    />

    <div class="mt-6">
      <app-empty-state
        icone="lock_clock"
        titulo="Funcionalidade indisponível"
        [descricao]="descricao()"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnavailableFeatureComponent {
  readonly titulo = input.required<string>();
  readonly descricao = input.required<string>();
}
