import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EMPTY, catchError, finalize, startWith, tap } from 'rxjs';

import { Empresa } from '@/app/model/empresa';
import {
  OrcamentoDetalhe,
  OrcamentoItem,
  OrcamentoRequest,
} from '@/app/model/orcamento';
import { STATUS_ORCAMENTO_CANONICO, rotuloStatusOrcamento } from '@/app/model/orcamento-presentation';
import { EmpresasService } from '@/app/services/empresas.service';
import { OrcamentosService } from '@/app/services/orcamentos.service';
import { DialogShellComponent } from '@/app/shared/components/dialog-shell/dialog-shell.component';
import { FileDropzoneComponent } from '@/app/shared/components/file-dropzone/file-dropzone.component';
import { trimmedEmailValidator } from '@/app/shared/validators/trimmed-email.validator';
import {
  calcularSubtotalEmCentavos,
  CENTAVOS_POR_REAL,
  converterCentavosParaValorSeguro,
  converterValorParaCentavosSeguro,
  LIMITE_AMOSTRAS_ORCAMENTO,
} from './orcamento-money';
import {
  ateDuasCasasDecimais,
  inteiroMinimoUm,
  textoObrigatorioSemEspacos,
} from './orcamento-form.validation';

const LIMITE_CODIGO_PROPOSTA = 80;
const LIMITE_NOME_PROPOSTA = 200;
const LIMITE_NOME_LABORATORIO = 120;
const LIMITE_DESCRICAO_SERVICO = 2000;
const LIMITE_ESCOPO = 2000;
const LIMITE_NOME_CONTATO = 150;
const LIMITE_EMAIL_CONTATO = 254;
const LIMITE_TIPO_ENSAIO = 200;
const LIMITE_NORMA = 255;

type ControlesItem = {
  testType: FormControl<string>;
  sampleCount: FormControl<number>;
  standard: FormControl<string>;
  unitPrice: FormControl<number>;
};

type GrupoItem = FormGroup<ControlesItem>;

export interface QuoteFormDialogData {
  readonly quoteId: string | null;
  readonly quoteCode: string | null;
}

export interface QuoteFormResult {
  readonly created: boolean;
  readonly quote: OrcamentoDetalhe;
}

interface StatusOption {
  readonly value: string;
  readonly label: string;
}

@Component({
  selector: 'app-quote-form-dialog',
  standalone: true,
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    DialogShellComponent,
    FileDropzoneComponent,
  ],
  templateUrl: './quote-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteFormDialogComponent {
  private readonly empresasService = inject(EmpresasService);
  private readonly orcamentosService = inject(OrcamentosService);
  private readonly dialogRef = inject(MatDialogRef<QuoteFormDialogComponent, QuoteFormResult>);
  private readonly destroyRef = inject(DestroyRef);
  private empresaOriginalId: string | null = null;

  readonly data = inject<QuoteFormDialogData>(MAT_DIALOG_DATA);
  readonly isEditing = this.data.quoteId !== null;
  readonly detalhe = signal<OrcamentoDetalhe | null>(null);
  readonly empresas = signal<readonly Empresa[]>([]);
  readonly statusOptions = signal<readonly StatusOption[]>(this.criarOpcoesStatus());
  readonly carregandoEmpresas = signal(true);
  readonly erroEmpresas = signal(false);
  readonly carregandoDetalhe = signal(this.isEditing);
  readonly erroDetalhe = signal(false);
  readonly erroDetalheMensagem = signal('');
  readonly saving = signal(false);
  readonly serverError = signal('');
  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal('');
  readonly totalCentavos = signal(0n);
  readonly totalIndisponivel = signal(false);
  readonly totalPrice = computed(() => this.totalIndisponivel()
    ? null
    : converterCentavosParaValorSeguro(this.totalCentavos()));
  readonly totalExcedido = computed(() => !this.totalIndisponivel()
    && converterCentavosParaValorSeguro(this.totalCentavos()) === null);

  readonly form = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [textoObrigatorioSemEspacos, Validators.maxLength(LIMITE_CODIGO_PROPOSTA)],
    }),
    companyId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, this.validarEmpresaDisponivel()],
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [textoObrigatorioSemEspacos, Validators.maxLength(LIMITE_NOME_PROPOSTA)],
    }),
    laboratoryName: new FormControl('', {
      nonNullable: true,
      validators: [textoObrigatorioSemEspacos, Validators.maxLength(LIMITE_NOME_LABORATORIO)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [textoObrigatorioSemEspacos, Validators.maxLength(LIMITE_DESCRICAO_SERVICO)],
    }),
    scope: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(LIMITE_ESCOPO)],
    }),
    externalContactName: new FormControl('', {
      nonNullable: true,
      validators: [textoObrigatorioSemEspacos, Validators.maxLength(LIMITE_NOME_CONTATO)],
    }),
    externalContactEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, trimmedEmailValidator, Validators.maxLength(LIMITE_EMAIL_CONTATO)],
    }),
    status: new FormControl('PendingAcceptance', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(new RegExp(`^(${STATUS_ORCAMENTO_CANONICO.join('|')})$`)),
      ],
    }),
    items: new FormArray<GrupoItem>([this.criarItem()], Validators.minLength(1)),
  });

  constructor() {
    this.form.controls.companyId.disable({ emitEvent: false });
    this.form.controls.companyId.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((companyId) => this.preencherContatoDaEmpresa(companyId));
    this.form.controls.items.valueChanges.pipe(
      startWith(this.form.controls.items.value),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.recalcularTotal());

    this.carregarEmpresas();
    if (this.data.quoteId) this.carregarDetalhe();
  }

  empresasDisponiveis(): readonly Empresa[] {
    const empresaSelecionada = this.detalhe()?.companyId ?? this.empresaOriginalId;
    return this.empresas().filter((empresa) =>
      empresa.status === 'ACTIVE' || (this.isEditing && empresa.id === empresaSelecionada),
    );
  }

  temEmpresaAtiva(): boolean {
    return this.empresas().some((empresa) => empresa.status === 'ACTIVE');
  }

  subtotalItem(item: GrupoItem): number | null {
    if (item.controls.sampleCount.invalid || item.controls.unitPrice.invalid) return null;
    const quantidade = Number(item.controls.sampleCount.value);
    const preco = Number(item.controls.unitPrice.value);
    const subtotal = calcularSubtotalEmCentavos(quantidade, preco);
    return subtotal === null ? null : converterCentavosParaValorSeguro(subtotal);
  }

  adicionarItem(): void {
    this.form.controls.items.push(this.criarItem());
  }

  removerItem(index: number): void {
    if (this.form.controls.items.length <= 1) return;
    this.form.controls.items.removeAt(index);
  }

  selecionarArquivo(arquivo: File | null): void {
    this.selectedFile.set(arquivo);
  }

  atualizarErroArquivo(erro: string | null): void {
    this.fileError.set(erro ?? '');
  }

  podeSalvar(): boolean {
    return !this.saving()
      && !this.carregandoEmpresas()
      && !this.erroEmpresas()
      && !this.totalExcedido()
      && (!this.isEditing || (!this.carregandoDetalhe() && !this.erroDetalhe() && this.detalhe() !== null));
  }

  close(): void {
    if (!this.saving()) this.dialogRef.close();
  }

  save(): void {
    if (this.saving() || !this.podeSalvar()) return;
    this.serverError.set('');

    if (this.form.invalid || this.fileError()) {
      this.form.markAllAsTouched();
      return;
    }

    const totalPrice = this.totalPrice();
    if (totalPrice === null) return;

    const request = this.criarRequisicao(totalPrice);
    if (!request) {
      this.form.markAllAsTouched();
      return;
    }
    const document = this.selectedFile() ?? undefined;
    this.saving.set(true);
    this.dialogRef.disableClose = true;

    const save$ = this.isEditing
      ? this.orcamentosService.update(this.data.quoteId as string, request, document)
      : this.orcamentosService.create(request, document);

    save$.pipe(
      catchError((error: HttpErrorResponse) => {
        this.serverError.set(this.descreverErro(error));
        return EMPTY;
      }),
      finalize(() => {
        this.dialogRef.disableClose = false;
        this.saving.set(false);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((quote) => this.dialogRef.close({ created: !this.isEditing, quote }));
  }

  tentarNovamenteEmpresas(): void {
    this.carregarEmpresas();
  }

  tentarNovamenteDetalhe(): void {
    if (this.data.quoteId) this.carregarDetalhe();
  }

  private criarItem(item?: OrcamentoItem): GrupoItem {
    return new FormGroup({
      testType: new FormControl(item?.testType ?? '', {
        nonNullable: true,
        validators: [textoObrigatorioSemEspacos, Validators.maxLength(LIMITE_TIPO_ENSAIO)],
      }),
      sampleCount: new FormControl(item?.sampleCount ?? 1, {
        nonNullable: true,
        validators: [Validators.required, Validators.min(1), Validators.max(LIMITE_AMOSTRAS_ORCAMENTO), inteiroMinimoUm],
      }),
      standard: new FormControl(item?.standard ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(LIMITE_NORMA)],
      }),
      unitPrice: new FormControl(item?.unitPrice ?? 0, {
        nonNullable: true,
        validators: [Validators.required, Validators.min(0), ateDuasCasasDecimais],
      }),
    });
  }

  private carregarEmpresas(): void {
    this.carregandoEmpresas.set(true);
    this.erroEmpresas.set(false);
    this.form.controls.companyId.disable({ emitEvent: false });
    this.empresasService.list('', null).pipe(
      tap((empresas) => {
        this.empresas.set([...empresas].sort((a, b) => a.legalName.localeCompare(b.legalName, 'pt-BR')));
        this.form.controls.companyId.enable({ emitEvent: false });
        this.form.controls.companyId.updateValueAndValidity({ emitEvent: false });
      }),
      catchError(() => {
        this.empresas.set([]);
        this.erroEmpresas.set(true);
        this.form.controls.companyId.disable({ emitEvent: false });
        return EMPTY;
      }),
      finalize(() => this.carregandoEmpresas.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private carregarDetalhe(): void {
    const quoteId = this.data.quoteId;
    if (!quoteId) return;
    this.carregandoDetalhe.set(true);
    this.erroDetalhe.set(false);
    this.erroDetalheMensagem.set('');
    this.orcamentosService.get(quoteId).pipe(
      tap((detalhe) => this.preencherFormulario(detalhe)),
      catchError((error: HttpErrorResponse) => {
        this.erroDetalhe.set(true);
        this.erroDetalheMensagem.set(error.status === 403
          ? 'Sua conta não tem permissão para editar este orçamento.'
          : error.status === 404
            ? 'Este orçamento não está mais disponível.'
            : 'Não foi possível carregar o orçamento. Confira sua conexão e tente novamente.');
        return EMPTY;
      }),
      finalize(() => this.carregandoDetalhe.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private preencherFormulario(detalhe: OrcamentoDetalhe): void {
    this.detalhe.set(detalhe);
    this.empresaOriginalId = detalhe.companyId;
    const status = detalhe.status;
    this.statusOptions.set(this.criarOpcoesStatus());

    this.form.patchValue({
      code: detalhe.code ?? '',
      companyId: detalhe.companyId,
      name: detalhe.name ?? '',
      laboratoryName: detalhe.laboratoryName ?? '',
      description: detalhe.description ?? '',
      scope: detalhe.scope ?? '',
      externalContactName: detalhe.externalContactName ?? '',
      externalContactEmail: detalhe.externalContactEmail ?? '',
      status,
    }, { emitEvent: false });

    const itens = this.form.controls.items;
    itens.clear({ emitEvent: false });
    if (detalhe.items.length) {
      detalhe.items.forEach((item) => itens.push(this.criarItem(item), { emitEvent: false }));
    } else {
      itens.push(this.criarItem(), { emitEvent: false });
    }
    this.form.controls.companyId.updateValueAndValidity({ emitEvent: false });
    this.form.markAsPristine();
    this.recalcularTotal();
  }

  private preencherContatoDaEmpresa(companyId: string): void {
    const empresa = this.empresas().find((item) => item.id === companyId);
    if (!empresa) return;

    const nomeContato = this.form.controls.externalContactName;
    const emailContato = this.form.controls.externalContactEmail;
    if (nomeContato.pristine) nomeContato.setValue(empresa.contactName, { emitEvent: false });
    if (emailContato.pristine) emailContato.setValue(empresa.contactEmail, { emitEvent: false });
  }

  private validarEmpresaDisponivel(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const empresa = this.empresas().find((item) => item.id === control.value);
      if (!empresa) return this.carregandoEmpresas() || this.erroEmpresas() ? null : { empresaInexistente: true };
      if (empresa.status === 'ACTIVE' || (this.isEditing && empresa.id === this.empresaOriginalId)) return null;
      return { empresaInativa: true };
    };
  }

  private recalcularTotal(): void {
    let valoresValidos = true;
    const centavos = this.form.controls.items.controls.reduce((total, item) => {
      if (item.controls.sampleCount.invalid || item.controls.unitPrice.invalid) {
        valoresValidos = false;
        return total;
      }
      const amostras = Number(item.controls.sampleCount.value);
      const valor = Number(item.controls.unitPrice.value);
      const subtotal = calcularSubtotalEmCentavos(amostras, valor);
      if (subtotal === null) {
        valoresValidos = false;
        return total;
      }
      return total + subtotal;
    }, 0n);
    this.totalCentavos.set(centavos);
    this.totalIndisponivel.set(!valoresValidos);
  }

  private criarRequisicao(totalPrice: number): OrcamentoRequest | null {
    const valor = this.form.getRawValue();
    const items: OrcamentoItem[] = [];
    for (const item of valor.items) {
      const unitPriceCentavos = converterValorParaCentavosSeguro(Number(item.unitPrice));
      if (unitPriceCentavos === null) return null;
      items.push({
        testType: item.testType.trim(),
        sampleCount: Number(item.sampleCount),
        standard: item.standard.trim() || null,
        unitPrice: unitPriceCentavos / CENTAVOS_POR_REAL,
      });
    }

    return {
      code: valor.code.trim(),
      companyId: valor.companyId,
      name: valor.name.trim(),
      laboratoryName: valor.laboratoryName.trim(),
      description: valor.description.trim(),
      scope: valor.scope.trim() || null,
      externalContactName: valor.externalContactName.trim(),
      externalContactEmail: valor.externalContactEmail.trim(),
      status: this.isEditing ? valor.status : 'PendingAcceptance',
      totalPrice,
      items,
    };
  }

  private descreverErro(error: HttpErrorResponse): string {
    if (error.status === 400) return this.mensagemDaApi(error) ?? 'Confira os campos informados.';
    if (error.status === 403) return 'Sua conta não tem permissão para manter orçamentos.';
    if (error.status === 404) return 'A empresa ou o orçamento não está mais disponível. Atualize os dados e tente novamente.';
    if (error.status === 409) return 'Este código de proposta já está cadastrado. Informe outro código.';
    if (error.status === 413) return 'O arquivo excede o tamanho máximo de 50 MB.';
    return 'Não foi possível salvar o orçamento. Verifique a conexão e tente novamente.';
  }

  private mensagemDaApi(error: HttpErrorResponse): string | null {
    return typeof error.error?.message === 'string' ? error.error.message : null;
  }

  private criarOpcoesStatus(): readonly StatusOption[] {
    return STATUS_ORCAMENTO_CANONICO.map((value) => ({
      value,
      label: rotuloStatusOrcamento(value),
    }));
  }
}
