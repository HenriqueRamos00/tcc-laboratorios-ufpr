import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { UserRole } from '@core/store/user-role.store';
import { Profile, ProfileAction } from '@/app/model/profile';
import { describeProfileError, emailExpiryLabel, hasProfileAction, roleLabel } from '@/app/model/profile-presentation';
import { ProfileService } from '@/app/services/profile.service';

import { ProfileEditDialogComponent, ProfileEditDialogData } from './profile-edit-dialog.component';
import { ProfileEmailDialogComponent, ProfileEmailDialogData } from './profile-email-dialog.component';
import { ProfilePasswordDialogComponent } from './profile-password-dialog.component';

type LoadState = 'loading' | 'loaded' | 'error';
type PendingAction = 'cancel-email' | null;

@Component({
  selector: 'app-internal-profile',
  standalone: true,
  imports: [PageHeaderComponent, MatButtonModule, MatIconModule, MatSnackBarModule],
  host: { '(window:focus)': 'refreshPendingEmailChange()' },
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent {
  private readonly profiles = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  private refreshingEmailChange = false;
  private readonly dialogs = inject(AppDialogService);
  private readonly login = inject(LoginService);
  private readonly router = inject(Router);
  private readonly userRole = inject(UserRole);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly loadState = signal<LoadState>('loading');
  protected readonly profile = signal<Profile | null>(null);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly pendingAction = signal<PendingAction>(null);
  protected readonly breadcrumbs = computed(() => [
    { label: 'Início', route: this.userRole.dashboardPath() },
    { label: 'Meu perfil' },
  ]);
  protected readonly initials = computed(() => initialsFor(this.profile()?.name ?? ''));
  protected readonly displayRole = computed(() => {
    const role = this.profile()?.role;
    return role ? roleLabel(role) : '';
  });
  protected readonly expiryLabel = computed(() => {
    const pending = this.profile()?.pendingEmailChange;
    return pending ? emailExpiryLabel(pending.expiresAt) : '';
  });

  constructor() {
    this.loadProfile();
  }

  protected loadProfile(): void {
    this.loadState.set('loading');
    this.errorMessage.set('');
    this.profiles.getProfile().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.login.updateProfileName(profile.name);
        this.loadState.set('loaded');
      },
      error: (error: unknown) => {
        this.errorMessage.set(describeProfileError(error));
        this.loadState.set('error');
      },
    });
  }

  protected refreshPendingEmailChange(): void {
    const pending = this.profile()?.pendingEmailChange;
    if (!pending || this.pendingAction() || this.refreshingEmailChange) return;

    this.refreshingEmailChange = true;
    this.profiles.getProfile().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => { this.refreshingEmailChange = false; }),
    ).subscribe({
      next: ({ pendingEmailChange }) => {
        this.profile.update((profile) => profile?.pendingEmailChange === pending && !this.pendingAction()
          ? { ...profile, pendingEmailChange }
          : profile);
      },
      error: () => { /* Mantém os dados da tela se a atualização em segundo plano falhar. */ },
    });
  }

  protected canDo(action: ProfileAction): boolean {
    const profile = this.profile();
    return !!profile && hasProfileAction(profile, action);
  }

  protected editPersonalData(): void {
    const profile = this.profile();
    if (!profile || !profile.editableFields.includes('name')) return;
    const data: ProfileEditDialogData = { profile };
    this.dialogs
      .open<ProfileEditDialogComponent, ProfileEditDialogData, Profile>(
        ProfileEditDialogComponent,
        data,
        'Editar dados pessoais',
        { disableClose: true },
      )
      .afterClosed()
      .subscribe((updated) => {
        if (updated) {
          this.profile.set(updated);
          this.login.updateProfileName(updated.name);
          this.successMessage.set('Dados pessoais atualizados.');
        }
      });
  }

  protected changeEmail(): void {
    const profile = this.profile();
    if (!profile || !this.canDo('change-email') || profile.pendingEmailChange) return;
    const data: ProfileEmailDialogData = { currentEmail: profile.email };
    this.dialogs
      .open<ProfileEmailDialogComponent, ProfileEmailDialogData, Profile['pendingEmailChange']>(
        ProfileEmailDialogComponent,
        data,
        'Alterar e-mail',
        { disableClose: true },
      )
      .afterClosed()
      .subscribe((pending) => {
        if (pending) {
          this.profile.update((current) => current ? { ...current, pendingEmailChange: pending } : current);
          this.successMessage.set('Solicitação registrada. Confirme os dois endereços de e-mail.');
        }
      });
  }

  protected changePassword(): void {
    const profile = this.profile();
    if (!profile || !this.canDo('change-password')) return;
    this.dialogs
      .open<ProfilePasswordDialogComponent, Profile, 'changed'>(
        ProfilePasswordDialogComponent,
        profile,
        'Alterar senha',
        { disableClose: true, maxHeight: 'calc(100dvh - 24px)' },
      )
      .afterClosed()
      .subscribe((result) => {
        if (result === 'changed') {
          this.snackBar.open('Senha alterada. Entre novamente para continuar.', 'Fechar', { duration: 6000 });
          this.login.logout();
          void this.router.navigateByUrl('/login');
        }
      });
  }

  protected cancelEmailChange(): void {
    if (this.pendingAction() || !this.profile()?.pendingEmailChange) return;
    this.pendingAction.set('cancel-email');
    this.errorMessage.set('');
    this.profiles.cancelEmailChange().subscribe({
      next: () => {
        this.profile.update((current) => current ? { ...current, pendingEmailChange: null } : current);
        this.successMessage.set('Solicitação de alteração de e-mail cancelada.');
        this.pendingAction.set(null);
      },
      error: (error: unknown) => {
        this.errorMessage.set(describeProfileError(error));
        this.pendingAction.set(null);
      },
    });
  }
}

function initialsFor(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toLocaleUpperCase('pt-BR');
}
