import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterOutlet } from '@angular/router';

import { LoginService } from '@/app/services/login.service';
import { LogoComponent } from '@shared/components/logo/logo.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [LogoComponent, MatButtonModule, RouterOutlet],
  template: `
    <header class="shell-header bg-lactec-nav text-white">
      <div class="mx-auto flex max-w-5xl items-center justify-between gap-4 px-5 py-4 sm:px-8">
        <div class="flex flex-wrap items-center gap-x-4 gap-y-0.5">
          <app-logo />
          <span class="text-sm">Administração Auth2</span>
        </div>
        <button mat-stroked-button type="button" (click)="login.signOut()">Sair</button>
      </div>
    </header>
    <main class="mx-auto max-w-5xl px-5 py-8 sm:px-8 sm:py-12">
      <router-outlet />
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly login = inject(LoginService);
}
