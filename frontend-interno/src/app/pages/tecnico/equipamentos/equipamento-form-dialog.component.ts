import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ValidatorFn, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { catchError, EMPTY, finalize } from 'rxjs';

import { Equipamento, EquipamentoRequest, EquipamentoRisco, EquipamentoTipo, EQUIPAMENTO_RISCOS, EQUIPAMENTO_TIPOS } from '@/app/model/equipamento';
import { EmpresaEquipamento, EquipamentosService } from '@/app/services/equipamentos.service';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

const textValidator = (max: number): ValidatorFn => (control) => {
  const value = control.value.trim();
  return !value ? { required: true } : value.length > max ? { maxlength: true } : null;
};

const voltageValidator: ValidatorFn = (control) => {
  const value = control.value.trim().replace(',', '.');
  if (!value) return { required: true };
  return /^[0-9]{1,6}(\.[0-9]{1,3})?$/.test(value) && Number(value) > 0 ? null : { voltage: true };
};

export interface EquipamentoFormDialogData {
  equipamento: Equipamento | null;
}

@Component({
  selector: 'app-equipamento-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, DialogShellComponent],
  templateUrl: './equipamento-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipamentoFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly equipmentService = inject(EquipamentosService);
  private readonly dialogRef = inject(MatDialogRef<EquipamentoFormDialogComponent, Equipamento>);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<EquipamentoFormDialogData>(MAT_DIALOG_DATA);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly loadingCompanies = signal(true);
  readonly companiesError = signal(false);
  readonly companyOptions = signal<EmpresaEquipamento[]>([]);
  readonly isEditing = this.data.equipamento !== null;
  readonly tipos = EQUIPAMENTO_TIPOS;
  readonly riscos = EQUIPAMENTO_RISCOS;

  readonly form = this.formBuilder.nonNullable.group({
    serialNumber: [this.data.equipamento?.serialNumber ?? '', textValidator(100)],
    tag: [this.data.equipamento?.tag ?? '', textValidator(100)],
    type: [this.data.equipamento?.type ?? ('' as EquipamentoTipo | ''), Validators.required],
    substation: [this.data.equipamento?.substation ?? '', textValidator(150)],
    primaryVoltage: [this.data.equipamento?.primaryVoltage ?? '', voltageValidator],
    companyId: [this.data.equipamento?.companyId ?? '', [Validators.required, (control: AbstractControl) =>
      this.companyOptions().some((empresa) => empresa.id === control.value) ? null : { company: true }]],
    riskLevel: [this.data.equipamento?.riskLevel ?? ('' as EquipamentoRisco | ''), Validators.required],
  });

  constructor() {
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.loadingCompanies.set(true);
    this.companiesError.set(false);
    this.equipmentService.listCompanies().pipe(
      catchError(() => {
        this.companiesError.set(true);
        return EMPTY;
      }),
      finalize(() => this.loadingCompanies.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((empresas) => {
      this.companyOptions.set(empresas.filter((empresa) =>
        empresa.status === 'ACTIVE' || empresa.id === this.data.equipamento?.companyId));
      this.form.controls.companyId.updateValueAndValidity();
    });
  }

  close(): void {
    if (!this.saving()) this.dialogRef.close();
  }

  save(): void {
    if (this.saving() || this.loadingCompanies() || this.companiesError()) return;
    this.serverError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (!value.type || !value.riskLevel) return;
    const request: EquipamentoRequest = {
      serialNumber: value.serialNumber.trim(),
      tag: value.tag.trim(),
      type: value.type,
      substation: value.substation.trim(),
      primaryVoltage: value.primaryVoltage.trim().replace(',', '.'),
      companyId: value.companyId,
      riskLevel: value.riskLevel,
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;
    const save$ = this.data.equipamento
      ? this.equipmentService.update(this.data.equipamento.id, request)
      : this.equipmentService.create(request);
    save$.pipe(
      catchError((error: HttpErrorResponse) => {
        this.serverError.set(error.status === 403 ? 'Sua conta não tem permissão para manter equipamentos.'
          : error.status === 404 ? 'Este equipamento não está mais disponível para edição.'
          : error.status === 400 && typeof error.error?.message === 'string' ? error.error.message
          : 'Não foi possível salvar. Verifique a conexão e tente novamente.');
        return EMPTY;
      }),
      finalize(() => {
        this.dialogRef.disableClose = false;
        this.saving.set(false);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((equipamento) => this.dialogRef.close(equipamento));
  }
}
