import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { InstrumentReference } from '@/app/model/ensaio';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

@Component({
  selector: 'app-instrumento-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, DialogShellComponent],
  templateUrl: './instrumento-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstrumentoFormDialogComponent {
  readonly dialogRef = inject(MatDialogRef<InstrumentoFormDialogComponent, InstrumentReference>);
  private readonly service = inject(EnsaiosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    inventoryNumber: ['', [Validators.required, Validators.maxLength(80)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    calibrationCertificate: ['', Validators.maxLength(160)],
    calibrationValidUntil: [''],
  });

  save(): void {
    if (this.saving()) return;
    const value = this.form.getRawValue();
    if (this.form.invalid || !value.inventoryNumber.trim() || !value.name.trim()) {
      this.form.markAllAsTouched();
      this.error.set('Informe o patrimônio e o nome do instrumento.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.dialogRef.disableClose = true;
    this.service.createInstrument({
      inventoryNumber: value.inventoryNumber.trim(), name: value.name.trim(),
      calibrationCertificate: value.calibrationCertificate.trim() || null,
      calibrationValidUntil: value.calibrationValidUntil || null,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (instrument) => this.dialogRef.close(instrument),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(typeof error.error?.message === 'string' ? error.error.message :
          'Não foi possível cadastrar o instrumento. Tente novamente.');
      },
    });
  }
}
