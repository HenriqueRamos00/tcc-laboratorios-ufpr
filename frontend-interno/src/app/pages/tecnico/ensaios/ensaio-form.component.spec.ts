import { By } from '@angular/platform-browser';
import { OverlayContainer } from '@angular/cdk/overlay';
import { MatSelect, MatSelectChange } from '@angular/material/select';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import {
  MeasurementTemplate,
  QuoteReference,
  SampleReference,
  TestDetail,
} from '@/app/model/ensaio';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { ReferenceSearchComponent } from '@shared/components/reference-search/reference-search.component';
import { EnsaioFormComponent } from './ensaio-form.component';

const quote: QuoteReference = {
  id: '8',
  code: 'HU25-001',
  companyId: '4',
  companyName: 'Empresa de demonstração',
};
const sample: SampleReference = {
  id: '31',
  code: 'A-HU25-0001/2026',
  description: 'Óleo do transformador TR-01',
  quote,
  analyzedEquipment: null,
};
const analyzedEquipment = {
  id: '55',
  tag: 'DEMO-TR-HU25-01',
  serialNumber: '111.594/1',
};
const saved: TestDetail = {
  id: '42',
  code: 'E-42/2026',
  sample: { id: sample.id, code: sample.code, description: sample.description },
  quote,
  type: 'Físico-químico',
  analysisType: 'physical-chemical',
  scheduledDate: '2026-10-26',
  responsible: { id: '7', name: 'Técnica responsável', unit: 'Laboratório', active: true },
  status: 'incomplete',
  version: 0,
  method: 'Método de ensaio',
  standard: 'Norma técnica',
  analyzedEquipment: null,
  equipmentIds: [],
  equipment: [],
  analysisClassification: null,
  reportText: null,
  measurements: [],
};

describe('Formulário de ensaio', () => {
  let fixture: ComponentFixture<EnsaioFormComponent>;
  let component: EnsaioFormComponent;
  let service: jasmine.SpyObj<EnsaiosService>;
  let router: jasmine.SpyObj<Router>;
  let dialog: jasmine.SpyObj<AppDialogService>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<EnsaiosService>('EnsaiosService', [
      'quotes', 'samples', 'analyzedEquipment', 'technicians', 'instruments',
      'get', 'template', 'create', 'update',
    ]);
    service.quotes.and.returnValue(of({ items: [quote], page: 1, pageSize: 20, totalItems: 1 }));
    service.samples.and.returnValue(of({ items: [sample], page: 1, pageSize: 20, totalItems: 1 }));
    service.analyzedEquipment.and.returnValue(of({ items: [], page: 1, pageSize: 20, totalItems: 0 }));
    service.technicians.and.returnValue(of([
      { id: '7', name: 'Técnica responsável', unit: 'Laboratório', active: true },
    ]));
    service.instruments.and.returnValue(of([]));
    service.get.and.returnValue(of(saved));
    const measurements: MeasurementTemplate[] = [
      { code: 'agua', name: 'Teor de água', unit: 'ppm', method: 'NBR 10710', referenceLimit: null },
      { code: 'densidade', name: 'Densidade', unit: 'g/mL', method: null, referenceLimit: null },
    ];
    service.template.and.returnValue(of({ analysisType: 'physical-chemical', measurements }));
    service.create.and.returnValue(of(saved));
    service.update.and.returnValue(of(saved));

    await TestBed.configureTestingModule({
      imports: [EnsaioFormComponent],
      providers: [
        provideRouter([]),
        { provide: EnsaiosService, useValue: service },
        { provide: AppDialogService, useValue: jasmine.createSpyObj('AppDialogService', ['open', 'openLateral']) },
      ],
    }).compileComponents();
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    dialog = TestBed.inject(AppDialogService) as jasmine.SpyObj<AppDialogService>;
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as never);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(EnsaioFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('abre a pesquisa de orçamento no painel e mantém o contexto ao cancelar', () => {
    prepareExistingSample();
    dialog.openLateral.and.returnValue({ afterClosed: () => of(undefined) } as never);
    component.openQuoteChooser();
    expect(dialog.openLateral).toHaveBeenCalled();
    expect(component.selectedQuote()).toEqual(quote);
    expect(component.selectedSample?.id).toBe(sample.id);
    expect(service.quotes).not.toHaveBeenCalled();
  });

  it('seleciona uma amostra existente pelo seletor compartilhado sem salvar o ensaio', () => {
    component.selectedQuote.set(quote);
    component.samples.set([sample]);
    component.sampleTotal.set(1);
    component.sampleLoading.set(false);
    component.sampleChooserOpen.set(true);
    fixture.detectChanges();

    const search = findReferenceSearch('sample-search');
    search.opened.set(true);
    fixture.detectChanges();
    const option = TestBed.inject(OverlayContainer).getContainerElement()
      .querySelector<HTMLButtonElement>('#sample-search-panel .reference-option')!;
    option.click();
    fixture.detectChanges();

    expect(component.form.controls.sampleId.value).toBe(sample.id);
    expect(component.selectedSample).toEqual(sample);
    expect(component.sampleChooserOpen()).toBeFalse();
    expect(service.create).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
  });

  it('associa equipamento analisado na nova identificação sem salvar o ensaio', () => {
    component.selectedQuote.set(quote);
    component.identificationMode.set('new');
    component.sampleChooserOpen.set(true);
    component.analyzedEquipment.set([analyzedEquipment]);
    component.equipmentTotal.set(1);
    component.equipmentLoading.set(false);
    component.form.patchValue({
      newSampleCode: 'A-HU25-NOVA/2026',
      newSampleDescription: 'Óleo do transformador TR-02',
      analyzedEquipmentId: null,
    });
    fixture.detectChanges();

    const search = findReferenceSearch('analyzed-equipment-search');
    search.opened.set(true);
    fixture.detectChanges();
    const option = TestBed.inject(OverlayContainer).getContainerElement()
      .querySelector<HTMLButtonElement>('#analyzed-equipment-search-panel .reference-option')!;
    option.click();
    fixture.detectChanges();

    expect(component.form.controls.analyzedEquipmentId.value).toBe(analyzedEquipment.id);
    expect(component.currentAnalyzedEquipment()).toEqual(analyzedEquipment);
    expect(component.equipmentSearch.value).toBe(analyzedEquipment.tag);
    expect(service.create).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
  });

  it('confirma a troca de orçamento antes de descartar a identificação nova', () => {
    prepareNewSample();
    const otherQuote = { ...quote, id: '99' };
    dialog.openLateral.and.returnValue({ afterClosed: () => of(otherQuote) } as never);
    component.openQuoteChooser();
    expect(component.selectedQuote()).toEqual(quote);
    expect(component.form.controls.newSampleCode.value).toBe('A-HU25-NOVA/2026');
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as never);
    component.openQuoteChooser();
    expect(component.selectedQuote()).toEqual(otherQuote);
    expect(component.form.controls.newSampleCode.value).toBe('');
    expect(component.form.controls.sampleId.value).toBeNull();
  });

  it('mostra Novo ensaio sem expor código ou placeholder na criação', () => {
    const heading = fixture.nativeElement.querySelector('header h1') as HTMLElement;

    expect(heading.textContent).toContain('Novo ensaio');
    expect(heading.textContent).not.toContain(saved.code);
    expect(fixture.nativeElement.textContent).not.toContain('Gerado após salvar');
    expect(fixture.nativeElement.textContent).not.toContain('Código do ensaio');
    expect(statusAction()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(
      'Salvar mantém este texto como rascunho e não gera nem disponibiliza PDF ao cliente.',
    );
  });

  it('mostra o código em badge junto ao título na edição', () => {
    component.editId.set(saved.id);
    component.detail.set(saved);
    fixture.detectChanges();
    const heading = fixture.nativeElement.querySelector('header h1') as HTMLElement;

    expect(heading.textContent).toContain('Editar ensaio');
    expect(heading.textContent).toContain('Código do ensaio:');
    expect(heading.textContent).toContain(saved.code);
    expect(heading.querySelector('.font-mono')?.textContent).toContain(saved.code);
  });

  it('abre a alteração de status pela edição de um ensaio persistido sem mudanças', () => {
    loadSavedEdit();
    fixture.detectChanges();

    expect(statusAction()?.disabled).toBeFalse();
    statusAction()?.click();

    expect(router.navigate).toHaveBeenCalledWith([
      '/tecnico/ensaios', saved.id, 'status',
    ]);
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('mantém o rascunho quando a pessoa continua editando antes de alterar o status', () => {
    loadSavedEdit();
    component.form.controls.reportText.setValue('Observação técnica ainda não salva');
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as never);

    component.openStatus();

    expect(dialog.open).toHaveBeenCalledWith(
      jasmine.anything(),
      jasmine.objectContaining({
        titulo: 'Descartar alterações do ensaio?',
        mensagem: jasmine.stringMatching(/texto técnico em rascunho/),
        textoCancelar: 'Continuar editando',
        textoConfirmar: 'Descartar e alterar status',
        perigo: true,
      }),
      'Confirmar descarte das alterações do ensaio',
    );
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.form.controls.reportText.value).toBe('Observação técnica ainda não salva');
    expect(service.update).not.toHaveBeenCalled();
  });

  it('descarta alterações somente após confirmação e não salva o rascunho automaticamente', () => {
    loadSavedEdit();
    component.form.controls.reportText.setValue('Rascunho que será descartado');
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as never);

    component.openStatus();

    expect(router.navigate).toHaveBeenCalledWith([
      '/tecnico/ensaios', saved.id, 'status',
    ]);
    expect(component.form.controls.reportText.value).toBe('Rascunho que será descartado');
    expect(service.update).not.toHaveBeenCalled();
  });

  it('bloqueia o acesso ao status enquanto o ensaio está salvando ou carregando', () => {
    component.editId.set(saved.id);
    component.detail.set(saved);
    component.saving.set(true);
    fixture.detectChanges();

    expect(statusAction()?.disabled).toBeTrue();
    component.openStatus();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();

    component.saving.set(false);
    component.detailLoading.set(true);
    fixture.detectChanges();
    expect(statusAction()?.disabled).toBeTrue();
    component.openStatus();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('carrega e permite editar um ensaio mantendo o orçamento histórico', () => {
    const orcamentoHistorico = { ...quote, code: 'ORC-CANCELADO-001' };
    const detalheHistorico = { ...saved, quote: orcamentoHistorico };
    service.get.and.returnValue(of(detalheHistorico));
    service.update.and.returnValue(of(detalheHistorico));
    component.editId.set(saved.id);
    component.reloadCurrent();
    fixture.detectChanges();

    expect(component.selectedQuote()).toEqual(orcamentoHistorico);
    expect(fixture.nativeElement.textContent).toContain('ORC-CANCELADO-001');
    expect(service.quotes).not.toHaveBeenCalled();

    component.save();

    expect(service.update).toHaveBeenCalledWith(saved.id, jasmine.objectContaining({
      sampleId: sample.id,
      newSample: null,
    }));
    expect(component.selectedQuote()).toEqual(orcamentoHistorico);
  });

  function prepareExistingSample(): void {
    component.selectedQuote.set(quote);
    component.identificationMode.set('existing');
    component.currentSample.set(sample);
    component.sampleChooserOpen.set(false);
    component.form.patchValue({
      sampleId: sample.id,
      type: 'Físico-químico',
      analysisType: 'physical-chemical',
      method: 'Método de ensaio',
      standard: 'Norma técnica',
      scheduledDate: '2026-10-26',
      responsibleId: '7',
      reportText: '',
    });
  }

  function loadSavedEdit(): void {
    component.editId.set(saved.id);
    component.reloadCurrent();
  }

  function statusAction(): HTMLButtonElement | null {
    const host = fixture.nativeElement as HTMLElement;
    return Array.from(host.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Alterar status')) ?? null;
  }

  function prepareNewSample(): void {
    component.selectedQuote.set(quote);
    component.identificationMode.set('new');
    component.sampleChooserOpen.set(false);
    component.form.patchValue({
      sampleId: null,
      newSampleCode: 'A-HU25-NOVA/2026',
      newSampleDescription: 'Óleo do transformador TR-02',
      analyzedEquipmentId: null,
      type: 'Cromatografia',
      analysisType: 'physical-chemical',
      method: 'Método de ensaio',
      standard: 'Norma técnica',
      scheduledDate: '2026-10-26',
      responsibleId: '7',
      reportText: '',
    });
    component.confirmedNewSampleCode.set('A-HU25-NOVA/2026');
  }

  function findReferenceSearch(fieldId: string): ReferenceSearchComponent {
    const search = fixture.debugElement.queryAll(By.directive(ReferenceSearchComponent))
      .map((debugElement) => debugElement.componentInstance as ReferenceSearchComponent)
      .find((candidate) => candidate.fieldId() === fieldId);
    if (!search) throw new Error(`Seletor não encontrado: ${fieldId}`);
    return search;
  }

  it('envia a amostra existente escolhida e preserva ND como texto', () => {
    prepareExistingSample();
    const row = component.measurements.at(0);
    row.get('resultKind')?.setValue('text');
    row.get('resultInput')?.setValue('ND');
    component.save();

    expect(service.create).toHaveBeenCalled();
    const request = service.create.calls.mostRecent().args[0];
    expect(request.sampleId).toBe(sample.id);
    expect(request.newSample).toBeNull();
    expect(request.measurements[0].result).toEqual({ kind: 'text', text: 'ND' });
    expect(request.measurements[1].result).toEqual({ kind: 'not-performed' });
  });

  it('permite o primeiro ensaio sem amostras ou instrumentos cadastrados', () => {
    prepareNewSample();
    component.samples.set([]);
    component.sampleTotal.set(0);
    component.instruments.set([]);
    component.save();

    expect(service.create).toHaveBeenCalled();
    expect(service.create.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({
      sampleId: null,
      newSample: {
        quoteId: quote.id,
        code: 'A-HU25-NOVA/2026',
        description: 'Óleo do transformador TR-02',
        analyzedEquipmentId: null,
      },
      equipmentIds: [],
    }));
  });

  it('exige confirmação explícita antes de enviar uma identificação nova', () => {
    prepareNewSample();
    component.confirmedNewSampleCode.set(null);
    component.save();

    expect(service.create).not.toHaveBeenCalled();
    expect(component.saveError()).toContain('confirme a identificação');
  });

  it('bloqueia os campos durante o envio e libera após a confirmação do servidor', () => {
    prepareExistingSample();
    const response = new Subject<TestDetail>();
    service.create.and.returnValue(response);
    component.save();
    fixture.detectChanges();

    expect(component.saving()).toBeTrue();
    expect(component.form.disabled).toBeTrue();
    expect(component.canDeactivate()).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Salvando');

    response.next(saved);
    response.complete();
    fixture.detectChanges();
    expect(component.saving()).toBeFalse();
    expect(component.form.enabled).toBeTrue();
    expect(component.saveSuccess()).toBeTrue();
    expect(component.canDeactivate()).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/tecnico/ensaios', saved.id, 'editar'],
      jasmine.objectContaining({
        replaceUrl: true,
        state: { testSavedConfirmation: true },
      }),
    );
  });

  it('mantém a confirmação de criação após abrir a edição do ensaio salvo', () => {
    spyOn(router, 'getCurrentNavigation').and.returnValue({
      extras: { state: { testSavedConfirmation: true } },
    } as never);

    const redirectedFixture = TestBed.createComponent(EnsaioFormComponent);

    expect(redirectedFixture.componentInstance.saveSuccess()).toBeTrue();
    redirectedFixture.destroy();
  });

  it('distingue conflito de código de amostra e mantém o preenchimento', () => {
    prepareNewSample();
    service.create.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'sample-code-conflict', message: 'Conflito' },
    })));
    component.save();

    expect(component.conflictType()).toBe('sample');
    expect(component.saveError()).toContain('código de amostra');
    expect(component.form.controls.newSampleCode.value).toBe('A-HU25-NOVA/2026');
    expect(component.form.controls.newSampleDescription.value).toBe('Óleo do transformador TR-02');
  });

  it('mostra a mensagem não vazia do conflito da API e preserva o rascunho', () => {
    prepareNewSample();
    service.create.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: '  O orçamento foi alterado enquanto o ensaio era preenchido.  ' },
    })));

    component.save();
    fixture.detectChanges();

    expect(component.saving()).toBeFalse();
    expect(component.form.enabled).toBeTrue();
    expect(component.saveError()).toBe('O orçamento foi alterado enquanto o ensaio era preenchido.');
    expect(fixture.nativeElement.textContent).toContain('O orçamento foi alterado enquanto o ensaio era preenchido.');
    expect(component.form.controls.newSampleCode.value).toBe('A-HU25-NOVA/2026');
    expect(component.form.controls.newSampleDescription.value).toBe('Óleo do transformador TR-02');
    expect(component.selectedQuote()).toEqual(quote);
  });

  it('usa a mensagem genérica para conflito sem mensagem da API e preserva o rascunho', () => {
    prepareNewSample();
    service.create.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: {},
    })));

    component.save();

    expect(component.saveError()).toBe('O salvamento encontrou um conflito. Seus dados continuam no formulário.');
    expect(component.form.controls.newSampleCode.value).toBe('A-HU25-NOVA/2026');
    expect(component.form.controls.newSampleDescription.value).toBe('Óleo do transformador TR-02');
    expect(component.selectedQuote()).toEqual(quote);
  });

  it('atualiza a busca por referência após conflito mesmo com o termo inalterado', fakeAsync(() => {
    prepareNewSample();
    const code = component.form.controls.newSampleCode.value;
    component.onNewSampleCodeChanged(code);
    tick(300);
    service.samples.calls.reset();

    component.openSampleSearchAfterConflict();

    expect(component.identificationMode()).toBe('existing');
    expect(component.sampleSearch.value).toBe(code);
    expect(service.samples).toHaveBeenCalledWith(quote.id, code, 1, 20);
  }));

  it('distingue conflito de versão e conserva a edição local', () => {
    prepareExistingSample();
    component.editId.set(saved.id);
    component.detail.set(saved);
    component.form.controls.type.setValue('Alteração ainda não salva');
    service.update.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'test-version-conflict', message: 'Conflito' },
    })));
    component.save();

    expect(component.conflictType()).toBe('version');
    expect(component.form.controls.type.value).toBe('Alteração ainda não salva');
  });

  it('preserva as linhas quando o modelo de resultados é alterado e cancelado', () => {
    component.measurements.at(0).get('method')?.setValue('Método alterado');
    component.measurements.markAsDirty();
    component.form.controls.analysisType.setValue('chromatography');
    component.changeAnalysisType('chromatography');

    expect(dialog.open).toHaveBeenCalled();
    expect(component.form.controls.analysisType.value).toBe('physical-chemical');
    expect(component.measurements.at(0).get('method')?.value).toBe('Método alterado');
  });

  it('restaura o seletor de resultado e preserva o texto ao cancelar a troca', () => {
    component.changeResultKind(0, 'text');
    component.measurements.at(0).get('resultInput')?.setValue('12,30');
    fixture.detectChanges();
    const selector = fixture.debugElement.query(By.css('td mat-select')).componentInstance as MatSelect;
    selector.value = 'quantity';
    selector.selectionChange.emit(new MatSelectChange(selector, 'quantity'));
    fixture.detectChanges();
    expect(selector.value).toBe('text');
    expect(component.measurements.at(0).get('resultInput')?.value).toBe('12,30');
    expect(fixture.nativeElement.querySelector('td textarea')).not.toBeNull();
  });

  it('impede sair da tela enquanto o envio está pendente', () => {
    component.saving.set(true);
    expect(component.canDeactivate()).toBeFalse();
    component.saving.set(false);
    expect(component.canDeactivate()).toBeTrue();
  });
});
