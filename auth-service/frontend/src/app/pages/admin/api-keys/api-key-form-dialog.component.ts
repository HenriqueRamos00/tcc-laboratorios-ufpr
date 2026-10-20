import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, EMPTY, finalize } from 'rxjs';

import { ApiKeyCreateRequest, ApiKeyCreateResponse } from '@/app/model/api-key';
import { ApiKeysService } from '@/app/services/api-keys.service';
import { DialogShellComponent } from '@/app/shared/components/dialog-shell/dialog-shell.component';

@Component({
  selector: 'app-api-key-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, DialogShellComponent, DatePipe],
  templateUrl: './api-key-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiKeyFormDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly apiKeys = inject(ApiKeysService);
  private readonly dialogRef = inject(MatDialogRef<ApiKeyFormDialogComponent, ApiKeyCreateResponse>);
  private readonly snackBar = inject(MatSnackBar);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly created = signal<ApiKeyCreateResponse | null>(null);
  readonly copyFeedback = signal('');

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(100)]],
    expiresAt: [''],
  });

  close(): void {
    if (this.saving()) return;
    this.dialogRef.close(this.created() ?? undefined);
  }

  save(): void {
    if (this.saving() || this.created()) return;
    this.serverError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const expiresAt = value.expiresAt
      ? value.expiresAt.length === 16
        ? `${value.expiresAt}:00`
        : value.expiresAt
      : null;
    const request: ApiKeyCreateRequest = {
      name: value.name.trim(),
      expiresAt,
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    this.apiKeys
      .create(request)
      .pipe(
        catchError(() => {
          this.serverError.set('Não foi possível criar a chave. Verifique os dados e tente novamente.');
          return EMPTY;
        }),
        finalize(() => {
          this.dialogRef.disableClose = false;
          this.saving.set(false);
        }),
      )
      .subscribe((response) => this.created.set(response));
  }

  async copyKey(): Promise<void> {
    const key = this.created()?.key;
    if (!key) return;
    try {
      await navigator.clipboard.writeText(key);
      this.copyFeedback.set('Chave copiada.');
    } catch {
      this.copyFeedback.set('Selecione a chave e copie manualmente.');
    }
    this.snackBar.open(this.copyFeedback(), 'Fechar', { duration: 2500 });
  }
}
