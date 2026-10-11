import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, EMPTY, finalize } from 'rxjs';

import { Cliente, ClienteEmpresa, ClienteStatus, ClienteUpdateRequest } from '@/app/model/cliente';
import { ClientesService } from '@/app/services/clientes.service';
import { EmpresasService } from '@/app/services/empresas.service';
import { trimmedEmailValidator } from '@shared/validators/trimmed-email.validator';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

interface EmpresaOption {
  id: string;
  legalName: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface ClienteFormDialogData {
  cliente: Cliente | null;
}

function fallbackOption(empresa: ClienteEmpresa): EmpresaOption {
  return { id: empresa.id, legalName: empresa.name, status: 'INACTIVE' };
}

@Component({
  selector: 'app-cliente-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatProgressSpinnerModule, DialogShellComponent],
  templateUrl: './cliente-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClienteFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly clients = inject(ClientesService);
  private readonly companies = inject(EmpresasService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogRef = inject(MatDialogRef<ClienteFormDialogComponent, Cliente>);
  readonly data = inject<ClienteFormDialogData>(MAT_DIALOG_DATA);
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly loadingCompanies = signal(true);
  readonly companiesError = signal(false);
  readonly companyOptions = signal<EmpresaOption[]>([]);
  readonly isEditing = this.data.cliente !== null;

  private readonly activeCompanyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const ids = Array.isArray(control.value) ? control.value as string[] : [];
    return this.companyOptions().some((empresa) => empresa.status === 'ACTIVE' && ids.includes(empresa.id))
      ? null
      : { activeCompanyRequired: true };
  };

  readonly form = this.formBuilder.nonNullable.group({
    name: [this.data.cliente?.name ?? '', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(150)]],
    email: [this.data.cliente?.email ?? '', [Validators.required, trimmedEmailValidator, Validators.maxLength(254)]],
    phone: [this.data.cliente?.phone ?? '', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(30)]],
    position: [this.data.cliente?.position ?? '', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(120)]],
    status: [this.data.cliente?.status ?? ('ACTIVE' as ClienteStatus), Validators.required],
    companyIds: this.formBuilder.nonNullable.control<string[]>(this.data.cliente?.companies.map((empresa) => empresa.id) ?? [], [Validators.required, this.activeCompanyValidator]),
    password: [
      '',
      [Validators.maxLength(72), Validators.pattern(/\S/), ...(this.data.cliente ? [] : [Validators.required])],
    ],
  });

  constructor() {
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.loadingCompanies.set(true);
    this.companiesError.set(false);
    this.companies.list('', null).pipe(
      catchError(() => {
        this.companiesError.set(true);
        return EMPTY;
      }),
      finalize(() => this.loadingCompanies.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((empresas) => {
      const vinculadas = this.data.cliente?.companies ?? [];
      const vinculadasPorId = new Map(vinculadas.map((empresa) => [empresa.id, empresa]));
      const options = new Map<string, EmpresaOption>();
      for (const empresa of empresas) {
        if (empresa.status === 'ACTIVE' || vinculadasPorId.has(empresa.id)) {
          options.set(empresa.id, { id: empresa.id, legalName: empresa.legalName, status: empresa.status });
        }
      }
      for (const empresa of vinculadas) {
        if (!options.has(empresa.id)) options.set(empresa.id, fallbackOption(empresa));
      }
      this.companyOptions.set([...options.values()].sort((a, b) => a.legalName.localeCompare(b.legalName, 'pt-BR')));
      this.form.controls.companyIds.updateValueAndValidity();
    });
  }

  empresaMarcada(id: string): boolean {
    return this.form.controls.companyIds.value.includes(id);
  }

  temEmpresaAtiva(): boolean {
    return this.companyOptions().some((empresa) => empresa.status === 'ACTIVE');
  }

  alternarEmpresa(empresa: EmpresaOption, event: Event): void {
    const marcada = (event.target as HTMLInputElement).checked;
    if (empresa.status !== 'ACTIVE' && !this.data.cliente?.companies.some((vinculo) => vinculo.id === empresa.id)) return;
    const idsAtuais = this.form.controls.companyIds.value;
    const ids = marcada
      ? [...new Set([...idsAtuais, empresa.id])]
      : idsAtuais.filter((id) => id !== empresa.id);
    this.form.controls.companyIds.setValue(ids);
    this.form.controls.companyIds.markAsTouched();
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
    const fields = {
      name: value.name.trim(),
      email: value.email.trim().toLowerCase(),
      phone: value.phone.trim(),
      position: value.position.trim(),
      status: value.status,
      companyIds: value.companyIds,
    };
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    const request: ClienteUpdateRequest = {
      ...fields,
      ...(value.password ? { password: value.password } : {}),
    };
    const saving$ = this.data.cliente
      ? this.clients.update(this.data.cliente.id, request)
      : this.clients.create({ ...fields, password: value.password });
    saving$.pipe(
      catchError((error: HttpErrorResponse) => {
        this.serverError.set(this.describeError(error));
        return EMPTY;
      }),
      finalize(() => this.finalizarSalvamento()),
    ).subscribe((cliente) => this.dialogRef.close(cliente));
  }

  private finalizarSalvamento(): void {
    this.dialogRef.disableClose = false;
    this.saving.set(false);
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 409) return 'Este e-mail já está cadastrado. Informe outro e-mail.';
    if (error.status === 400) return this.apiMessage(error) ?? 'Confira os campos e as empresas selecionadas.';
    if (error.status === 403) return 'Sua conta não tem permissão para manter clientes.';
    return 'Não foi possível salvar. Verifique a conexão e tente novamente.';
  }

  private apiMessage(error: HttpErrorResponse): string | null {
    return typeof error.error?.message === 'string' ? error.error.message : null;
  }
}
