import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { take } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { LogoComponent } from '@shared/components/logo/logo.component';
import { ProfileService } from '@/app/services/profile.service';
import { LoginService } from '@/app/services/login.service';
import { describeProfileError } from '@/app/model/profile-presentation';

type ConfirmationState = 'ready' | 'submitting' | 'awaiting' | 'completed' | 'error' | 'missing';

@Component({
  selector: 'app-confirm-email-change',
  standalone: true,
  imports: [LogoComponent, RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './confirm-email-change.component.html',
  styleUrl: './confirm-email-change.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmEmailChangeComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly profiles = inject(ProfileService);
  private readonly login = inject(LoginService);

  protected readonly state = signal<ConfirmationState>('missing');
  protected readonly errorMessage = signal('');
  private readonly token = signal<string | null>(null);

  constructor() {
    this.route.queryParamMap.pipe(take(1)).subscribe((parameters) => {
      const token = parameters.get('token');
      if (!token?.trim()) return;
      this.token.set(token);
      this.state.set('ready');
      this.location.replaceState('/confirmar-alteracao-email');
    });
  }

  protected confirm(): void {
    const token = this.token();
    if (!token || this.state() !== 'ready') return;
    this.state.set('submitting');
    this.errorMessage.set('');
    this.profiles.confirmEmailChange(token).subscribe({
      next: (response) => {
        this.token.set(null);
        if (response.status === 'completed') {
          this.login.logout();
          this.state.set('completed');
        } else {
          this.state.set('awaiting');
        }
      },
      error: (error: unknown) => {
        this.token.set(null);
        this.errorMessage.set(describeProfileError(error));
        this.state.set('error');
      },
    });
  }
}
