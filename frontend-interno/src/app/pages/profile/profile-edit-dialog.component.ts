import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EMPTY, catchError, finalize } from 'rxjs';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { Profile, ProfileUpdateRequest } from '@/app/model/profile';
import { describeProfileError } from '@/app/model/profile-presentation';
import { ProfileService } from '@/app/services/profile.service';

export interface ProfileEditDialogData {
  readonly profile: Profile;
}

@Component({
  selector: 'app-profile-edit-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DialogShellComponent, MatButtonModule],
  templateUrl: './profile-edit-dialog.component.html',
  styleUrl: './profile-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileEditDialogComponent {
  readonly data = inject<ProfileEditDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ProfileEditDialogComponent, Profile>);
  private readonly profiles = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly discardConfirmation = signal(false);
  protected readonly serverError = signal('');
  protected readonly form = this.formBuilder.nonNullable.group({
    name: [this.data.profile.name, [Validators.required, Validators.pattern(/\S/)]],
  });
  protected readonly requestClose = () => this.close();

  protected nameError(): string {
    if (this.form.controls.name.hasError('required')) return 'Informe seu nome.';
    if (this.form.controls.name.hasError('pattern')) return 'Informe um nome que contenha caracteres além de espaços.';
    return 'Confira o nome informado.';
  }

  protected hasChanges(): boolean {
    return this.form.controls.name.value.trim() !== this.data.profile.name;
  }

  protected close(): void {
    if (this.saving()) return;
    if (this.hasChanges()) {
      this.discardConfirmation.set(true);
      return;
    }
    this.dialogRef.close();
  }

  protected continueEditing(): void {
    this.discardConfirmation.set(false);
  }

  protected discardChanges(): void {
    if (!this.saving()) this.dialogRef.close();
  }

  protected save(): void {
    if (this.saving()) return;
    this.serverError.set('');
    this.discardConfirmation.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.hasChanges()) return;

    const request: ProfileUpdateRequest = { name: this.form.controls.name.value.trim() };
    this.saving.set(true);
    this.dialogRef.disableClose = true;
    this.profiles.updatePersonalData(request).pipe(
      catchError((error: unknown) => {
        this.serverError.set(describeProfileError(error));
        return EMPTY;
      }),
      finalize(() => {
        this.saving.set(false);
        this.dialogRef.disableClose = false;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((profile) => this.dialogRef.close(profile));
  }
}
