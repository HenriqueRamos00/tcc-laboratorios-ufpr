import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import pt from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';

import { Empresa } from '@/app/model/empresa';
import { OrcamentoDetalhe, OrcamentoRequest } from '@/app/model/orcamento';
import { EmpresasService } from '@/app/services/empresas.service';
import { OrcamentosService } from '@/app/services/orcamentos.service';
import { QuoteFormDialogComponent, QuoteFormDialogData } from './quote-form-dialog.component';

registerLocaleData(pt);

describe('Formulário de orçamento', () => {
  let empresasService: jasmine.SpyObj<EmpresasService>;
  let orcamentosService: jasmine.SpyObj<OrcamentosService>;
  let dialogRef: MatDialogRef<QuoteFormDialogComponent> & { close: jasmine.Spy };
  let dadosDialogo: { quoteId: string | null; quoteCode: string | null };

  const empresaAtiva: Empresa = {
    id: 'company-1',
    legalName: 'Copel Distribuição',
    cnpj: '11222333000181',
    address: 'Rua das Araucárias, 10',
    city: 'Curitiba',
    state: 'PR',
    contactName: 'Ana Lima',
    contactPhone: '(41) 3333-4444',
    contactEmail: 'ana@copel.example',
    status: 'ACTIVE',
    origin: 'LOCAL',
  };

  const segundaEmpresa: Empresa = {
    ...empresaAtiva,
    id: 'company-2',
    legalName: 'Laboratórios do Sul',
    contactName: 'Bruno Alves',
    contactEmail: 'bruno@sul.example',
  };

  const detalhe: OrcamentoDetalhe = {
    id: 'quote-1',
    code: 'EAQ_2026_45482_V_1',
    name: 'Análise de óleo isolante',
    status: 'PendingAcceptance',
    stage: 'Negotiation',
    description: 'Análise físico-química de óleo isolante',
    companyName: 'Copel Distribuição',
    externalContactName: 'Ana Lima',
    externalContactEmail: 'ana@copel.example',
    totalPrice: 37.5,
    createdDate: '2026-10-15T10:00:00Z',
    hasDocument: true,
    companyId: 'company-1',
    laboratoryName: 'Laboratório de Ensaios',
    scope: 'Análise conforme plano de amostragem',
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533', unitPrice: 12.5 }],
  };

  function novaRequisicao(): OrcamentoRequest {
    return {
      code: 'EAQ_2026_45482_V_1',
      companyId: 'company-1',
      name: 'Análise de óleo isolante',
      laboratoryName: 'Laboratório de Ensaios',
      description: 'Análise físico-química de óleo isolante',
      scope: 'Análise conforme plano de amostragem',
      externalContactName: 'Ana Lima',
      externalContactEmail: 'ana@copel.example',
      status: 'PendingAcceptance',
      totalPrice: 37.5,
      items: [{ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533', unitPrice: 12.5 }],
    };
  }

  function preencherFormulario(component: QuoteFormDialogComponent): void {
    component.form.patchValue({
      code: ' EAQ_2026_45482_V_1 ',
      companyId: 'company-1',
      name: 'Análise de óleo isolante',
      laboratoryName: 'Laboratório de Ensaios',
      description: 'Análise físico-química de óleo isolante',
      scope: 'Análise conforme plano de amostragem',
      externalContactName: 'Ana Lima',
      externalContactEmail: 'ana@copel.example',
    });
    const item = component.form.controls.items.at(0);
    item.patchValue({ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533', unitPrice: 12.5 });
  }

  function criarFormulario(data: QuoteFormDialogData = { quoteId: null, quoteCode: null }) {
    Object.assign(dadosDialogo, data);
    const fixture = TestBed.createComponent(QuoteFormDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    dadosDialogo = { quoteId: null, quoteCode: null };
    empresasService = jasmine.createSpyObj<EmpresasService>('EmpresasService', ['list']);
    empresasService.list.and.returnValue(of([empresaAtiva, segundaEmpresa]));
    orcamentosService = jasmine.createSpyObj<OrcamentosService>(
      'OrcamentosService', ['get', 'create', 'update'],
    );
    orcamentosService.get.and.returnValue(of(detalhe));
    orcamentosService.create.and.returnValue(of(detalhe));
    orcamentosService.update.and.returnValue(of(detalhe));
    dialogRef = {
      close: jasmine.createSpy('close'),
      disableClose: false,
    } as unknown as MatDialogRef<QuoteFormDialogComponent> & { close: jasmine.Spy };

    TestBed.configureTestingModule({
      imports: [QuoteFormDialogComponent],
      providers: [
        { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { timezone: '-0300' } },
        { provide: EmpresasService, useValue: empresasService },
        { provide: OrcamentosService, useValue: orcamentosService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: dadosDialogo },
      ],
    });
  });

  it('preenche o contato da empresa e preserva o campo que foi editado ao trocar empresa', fakeAsync(() => {
    const fixture = criarFormulario();
    const component = fixture.componentInstance;

    component.form.controls.companyId.setValue('company-1');
    expect(component.form.controls.externalContactName.value).toBe('Ana Lima');
    expect(component.form.controls.externalContactEmail.value).toBe('ana@copel.example');

    component.form.controls.externalContactName.setValue('Contato específico');
    component.form.controls.externalContactName.markAsDirty();
    component.form.controls.companyId.setValue('company-2');

    expect(component.form.controls.externalContactName.value).toBe('Contato específico');
    expect(component.form.controls.externalContactEmail.value).toBe('bruno@sul.example');
    fixture.destroy();
  }));

  it('só permite empresas ativas na criação e mantém a empresa original inativa na edição', fakeAsync(() => {
    const empresaInativa: Empresa = { ...segundaEmpresa, status: 'INACTIVE' };
    empresasService.list.and.returnValue(of([empresaAtiva, empresaInativa]));
    const criacao = criarFormulario();
    expect(criacao.componentInstance.empresasDisponiveis().map((empresa) => empresa.id))
      .toEqual(['company-1']);
    criacao.destroy();

    orcamentosService.get.and.returnValue(of({ ...detalhe, companyId: 'company-2' }));
    const edicao = criarFormulario({ quoteId: 'quote-1', quoteCode: detalhe.code });
    expect(edicao.componentInstance.empresasDisponiveis().map((empresa) => empresa.id))
      .toEqual(['company-1', 'company-2']);
    expect(edicao.componentInstance.form.controls.companyId.value).toBe('company-2');
    edicao.destroy();
  }));

  it('calcula o total em centavos e valida quantidade inteira e preço com até duas casas', fakeAsync(() => {
    const fixture = criarFormulario();
    const component = fixture.componentInstance;
    component.form.controls.companyId.setValue('company-1');
    const item = component.form.controls.items.at(0);
    item.patchValue({ testType: 'Ensaio de calibração', sampleCount: 3, unitPrice: 0.1 });

    expect(component.totalPrice()).toBe(0.3);
    item.controls.sampleCount.setValue(1.5);
    item.controls.sampleCount.markAsTouched();
    expect(item.controls.sampleCount.hasError('inteiro')).toBeTrue();
    item.controls.sampleCount.setValue(2147483648);
    expect(item.controls.sampleCount.hasError('max')).toBeTrue();
    item.controls.sampleCount.setValue(3);
    item.controls.unitPrice.setValue(1.005);
    item.controls.unitPrice.markAsTouched();
    expect(item.controls.unitPrice.hasError('casasDecimais')).toBeTrue();
    item.controls.unitPrice.setValue(1e-9);
    expect(item.controls.unitPrice.hasError('casasDecimais')).toBeTrue();

    component.adicionarItem();
    component.form.controls.items.at(1).patchValue({ testType: 'Outro ensaio', sampleCount: 1, unitPrice: 0.2 });
    item.controls.unitPrice.setValue(0.1);
    expect(component.totalPrice()).toBe(0.5);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('0,30');
    expect(fixture.nativeElement.textContent).toContain('0,50');
    fixture.destroy();
  }));

  it('aguarda o detalhe, apresenta erro e permite repetir a carga antes de habilitar o salvamento', fakeAsync(() => {
    const primeiraResposta = new Subject<OrcamentoDetalhe>();
    orcamentosService.get.and.returnValues(primeiraResposta, of(detalhe));
    const fixture = criarFormulario({ quoteId: 'quote-1', quoteCode: detalhe.code });
    const submit = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;

    expect(orcamentosService.get).toHaveBeenCalledOnceWith('quote-1');
    expect(fixture.componentInstance.carregandoDetalhe()).toBeTrue();
    expect(submit.disabled).toBeTrue();

    primeiraResposta.error(new HttpErrorResponse({ status: 503, statusText: 'Unavailable' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar o orçamento.');
    expect(submit.disabled).toBeTrue();

    const retry = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === 'Tentar novamente');
    expect(retry).toBeDefined();
    retry?.click();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(orcamentosService.get).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.detalhe()?.companyId).toBe('company-1');
    expect(fixture.componentInstance.podeSalvar()).toBeTrue();
    fixture.destroy();
  }));

  it('não envia dados ao cancelar o formulário', fakeAsync(() => {
    const fixture = criarFormulario();
    fixture.componentInstance.close();

    expect(dialogRef.close).toHaveBeenCalledOnceWith();
    expect(orcamentosService.create).not.toHaveBeenCalled();
    expect(orcamentosService.update).not.toHaveBeenCalled();
    fixture.destroy();
  }));

  it('envia uma única criação com código aparado, status inicial e total calculado', fakeAsync(() => {
    const resposta = new Subject<OrcamentoDetalhe>();
    orcamentosService.create.and.returnValue(resposta);
    const fixture = criarFormulario();
    const component = fixture.componentInstance;
    preencherFormulario(component);
    component.save();
    component.save();

    expect(orcamentosService.create).toHaveBeenCalledTimes(1);
    expect(orcamentosService.create).toHaveBeenCalledWith(novaRequisicao(), undefined);
    expect(dialogRef.disableClose).toBeTrue();
    resposta.next(detalhe);
    resposta.complete();
    expect(dialogRef.close).toHaveBeenCalledOnceWith({ created: true, quote: detalhe });
    expect(dialogRef.disableClose).toBeFalse();
    fixture.destroy();
  }));

  it('carrega detalhe antes de editar e preserva o PDF atual sem nova parte de arquivo', fakeAsync(() => {
    const fixture = criarFormulario({ quoteId: 'quote-1', quoteCode: detalhe.code });
    const component = fixture.componentInstance;
    expect(component.detalhe()?.companyId).toBe('company-1');
    expect(component.form.controls.items.length).toBe(1);
    expect(component.form.controls.code.value).toBe(detalhe.code);

    component.save();

    expect(orcamentosService.update).toHaveBeenCalledTimes(1);
    const [id, request, document] = orcamentosService.update.calls.mostRecent().args;
    expect(id).toBe('quote-1');
    expect(request.status).toBe('PendingAcceptance');
    expect(request.totalPrice).toBe(37.5);
    expect(document).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('O PDF atual será mantido');
    fixture.destroy();
  }));

  it('exige escolher um status comercial ao editar orçamento com status desconhecido', fakeAsync(() => {
    const comStatusDesconhecido = { ...detalhe, status: 'Aguardando validação histórica' };
    orcamentosService.get.and.returnValue(of(comStatusDesconhecido));
    const fixture = criarFormulario({ quoteId: 'quote-1', quoteCode: detalhe.code });
    const component = fixture.componentInstance;

    expect(component.form.controls.status.value).toBe('Aguardando validação histórica');
    expect(component.statusOptions().some((option) => option.value === 'Aguardando validação histórica')).toBeFalse();
    expect(component.form.controls.status.invalid).toBeTrue();
    component.form.controls.status.setValue('InProgress');
    component.save();
    expect(orcamentosService.update.calls.mostRecent().args[1].status)
      .toBe('InProgress');
    fixture.destroy();
  }));

  it('anexa um PDF novo e mantém os dados preenchidos quando o servidor rejeita o salvamento', fakeAsync(() => {
    const fixture = criarFormulario();
    const component = fixture.componentInstance;
    preencherFormulario(component);
    const pdf = new File(['pdf'], 'proposta.pdf', { type: 'application/pdf' });
    component.selecionarArquivo(pdf);
    orcamentosService.create.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' })),
    );

    component.save();

    expect(orcamentosService.create).toHaveBeenCalledWith(novaRequisicao(), pdf);
    expect(component.serverError()).toContain('código de proposta já está cadastrado');
    expect(component.form.controls.code.value).toBe(' EAQ_2026_45482_V_1 ');
    expect(component.selectedFile()).toBe(pdf);
    expect(dialogRef.close).not.toHaveBeenCalled();
    fixture.destroy();
  }));

  it('bloqueia o envio quando o total não pode ser representado com precisão segura', fakeAsync(() => {
    const fixture = criarFormulario();
    const component = fixture.componentInstance;
    preencherFormulario(component);
    const item = component.form.controls.items.at(0);
    item.controls.sampleCount.setValue(2147483647);
    item.controls.unitPrice.setValue(5000000);
    fixture.detectChanges();

    expect(component.totalExcedido()).toBeTrue();
    expect(component.totalPrice()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Valor excede a precisão numérica segura.');
    component.save();
    expect(orcamentosService.create).not.toHaveBeenCalled();
    fixture.destroy();
  }));
});
