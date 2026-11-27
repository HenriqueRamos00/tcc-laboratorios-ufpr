import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { EMPTY, catchError, finalize } from 'rxjs';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { TextFieldComponent } from '@shared/components/text-field/text-field.component';
import { Profile, ProfileUpdateRequest } from '@/app/model/profile';
import { describeProfileError } from '@/app/model/profile-presentation';
import { ProfileService } from '@/app/services/profile.service';

export interface ProfileEditDialogData {
  readonly profile: Profile;
}

@Component({
  selector: 'app-profile-edit-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DialogShellComponent, TextFieldComponent, MatButtonModule, MatIconModule],
  templateUrl: './profile-edit-dialog.component.html',
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
  protected readonly hasPhone = this.data.profile.editableFields.includes('phone');
  protected readonly hasPosition = this.data.profile.editableFields.includes('position');
  protected readonly form = this.formBuilder.nonNullable.group({
    name: [this.data.profile.name, [Validators.required, Validators.pattern(/\S/)]],
    phone: [this.data.profile.phone ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(30)]],
    position: [this.data.profile.position ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
  });

  protected readonly requestClose = () => this.close();

  constructor() {
    if (!this.hasPhone) this.form.controls.phone.disable();
    if (!this.hasPosition) this.form.controls.position.disable();
  }

  protected nameError(): string {
    if (this.form.controls.name.hasError('required')) return 'Informe seu nome.';
    if (this.form.controls.name.hasError('pattern')) return 'Informe um nome que contenha caracteres além de espaços.';
    return 'Confira o nome informado.';
  }

  protected phoneError(): string {
    if (this.form.controls.phone.hasError('required')) return 'Informe seu telefone.';
    if (this.form.controls.phone.hasError('pattern')) return 'Informe um telefone que contenha números.';
    if (this.form.controls.phone.hasError('maxlength')) return 'O telefone deve ter até 30 caracteres.';
    return 'Confira o telefone informado.';
  }

  protected positionError(): string {
    if (this.form.controls.position.hasError('required')) return 'Informe seu cargo.';
    if (this.form.controls.position.hasError('pattern')) return 'Informe um cargo que contenha caracteres além de espaços.';
    if (this.form.controls.position.hasError('maxlength')) return 'O cargo deve ter até 120 caracteres.';
    return 'Confira o cargo informado.';
  }

  protected hasChanges(): boolean {
    const value = this.form.getRawValue();
    return value.name.trim() !== this.data.profile.name
      || (this.hasPhone && value.phone.trim() !== (this.data.profile.phone ?? ''))
      || (this.hasPosition && value.position.trim() !== (this.data.profile.position ?? ''));
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
    if (this.saving()) return;
    this.dialogRef.close();
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

    const values = this.form.getRawValue();
    const request: ProfileUpdateRequest = {
      name: values.name.trim(),
      ...(this.hasPhone ? { phone: values.phone.trim() } : {}),
      ...(this.hasPosition ? { position: values.position.trim() } : {}),
    };
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
