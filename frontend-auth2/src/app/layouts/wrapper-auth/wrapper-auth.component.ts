import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { LogoComponent } from '@shared/components/logo/logo.component';

@Component({
  selector: 'app-wrapper-auth',
  standalone: true,
  imports: [LogoComponent, RouterOutlet],
  template: `
    <main class="grid min-h-svh grid-rows-[auto_1fr] bg-lactec-paper md:grid-cols-2 md:grid-rows-1">
      <div class="bg-lactec-nav bg-login-glow grid place-items-center px-6 py-10 text-white md:px-10">
        <div class="grid justify-items-center gap-4 text-center">
          <app-logo />
          <p class="font-display text-2xl font-semibold tracking-tight md:text-3xl">
            Administração Auth2
          </p>
        </div>
      </div>
      <div class="grid place-items-center px-6 py-10 md:px-10">
        <router-outlet />
      </div>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WrapperAuthComponent {}
