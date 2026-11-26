import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActionButtonComponent } from '@shared/components/action-button/action-button.component';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [ActionButtonComponent],
  template: `
    <main class="min-h-svh grid place-items-center bg-lactec-surface px-6">
      <div class="text-center max-w-md">
        <p class="display text-6xl font-semibold text-lactec-nav leading-none m-0">404</p>
        <h1 class="display text-2xl md:text-3xl font-semibold text-lactec-ink mt-4 mb-2">
          Página não encontrada
        </h1>
        <p class="text-lactec-muted text-sm m-0">O endereço acessado não existe ou foi movido.</p>
        <app-action-button label="Voltar ao início" linkTo="/" class="mt-6" />
      </div>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
