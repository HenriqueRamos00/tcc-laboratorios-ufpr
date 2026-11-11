import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { QuoteReference, SampleReference, TestDetail, TestStatusHistoryEntry } from '@/app/model/ensaio';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { EnsaioStatusComponent } from './ensaio-status.component';

const quote: QuoteReference = {
  id: '8', code: 'ORC-2026-008', companyId: '4', companyName: 'Empresa de demonstração',
};
const sample: SampleReference = {
  id: '31', code: 'AM-2026-031', description: 'Óleo do transformador TR-01',
  quote, analyzedEquipment: { id: '21', tag: 'TR-01', serialNumber: 'SN-01' },
};
const testDetail: TestDetail = {
  id: '42', code: 'E-42/2026', sample: { id: sample.id, code: sample.code, description: sample.description },
  quote, type: 'Físico-químico', analysisType: 'physical-chemical',
  scheduledDate: '2026-10-26', responsible: { id: '7', name: 'Técnica responsável', unit: 'Laboratório' },
  status: 'incomplete', version: 4, method: 'Método de ensaio', standard: 'Norma técnica',
  analyzedEquipment: sample.analyzedEquipment, equipmentIds: [], equipment: [],
  analysisClassification: null, reportText: null, measurements: [],
};
const statusHistory: TestStatusHistoryEntry[] = [{
  id: '101', previousStatus: 'incomplete', status: 'validated', action: 'set-status',
  justification: null, changedAt: '2026-11-01T15:00:00Z',
  responsible: { id: '7', name: 'Técnica responsável', unit: 'Laboratório' },
}];

describe('Acompanhamento de status do ensaio', () => {
  let fixture: ComponentFixture<EnsaioStatusComponent>;
  let component: EnsaioStatusComponent;
  let service: jasmine.SpyObj<EnsaiosService>;
  let dialog: jasmine.SpyObj<AppDialogService>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<EnsaiosService>('EnsaiosService', [
      'get', 'statusHistory', 'updateStatus', 'reportsForTest', 'prepareReport',
    ]);
    service.get.and.returnValue(of(testDetail));
    service.statusHistory.and.returnValue(of(statusHistory));
    service.reportsForTest.and.returnValue(of({ items: [], publishedDocumentAvailable: false }));
    service.prepareReport.and.returnValue(of({
      outcome: 'created', reportId: '91',
      reports: [{ id: '91', validationStatus: 'pending-validation', published: false, publishedAt: null, version: 0 }],
    }));
    service.updateStatus.and.returnValue(of({
      id: testDetail.id, status: 'validated', changedAt: '2026-11-01T15:00:00Z',
      responsible: testDetail.responsible, version: 5, changed: true,
    }));
    dialog = jasmine.createSpyObj<AppDialogService>('AppDialogService', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);

    await TestBed.configureTestingModule({
      imports: [EnsaioStatusComponent],
      providers: [
        provideRouter([{ path: 'tecnico/relatorios/:id', children: [] }]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '42' } } } },
        { provide: EnsaiosService, useValue: service },
        { provide: AppDialogService, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EnsaioStatusComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function selecionarNoControle(status: string): void {
    const select = fixture.nativeElement.querySelector('#new-test-status') as HTMLSelectElement;
    select.value = status;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
  }

  function botaoComTexto(texto: string): HTMLButtonElement {
    return Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes(texto))!;
  }

  it('apresenta o status, equipamentos e histórico carregados do ensaio', () => {
    expect(fixture.nativeElement.textContent).toContain('Em execução');
    const etapaAtual = fixture.nativeElement.querySelector('[aria-current="step"]') as HTMLElement;
    expect(etapaAtual.textContent).toContain('Em execução');
    expect(fixture.nativeElement.textContent).toContain('TR-01');
    expect(fixture.nativeElement.textContent).toContain('Técnica responsável');
    expect(fixture.nativeElement.textContent).toContain('Validado');
    expect(service.get).toHaveBeenCalledWith('42');
    expect(service.statusHistory).toHaveBeenCalledWith('42');
  });

  it('permite escolher uma etapa diretamente e envia a versão atual', () => {
    component.selectStatus('validated');
    component.updateSelectedStatus();

    expect(service.updateStatus).toHaveBeenCalledWith('42', {
      action: 'set-status', targetStatus: 'validated', version: 4,
    });
    expect(component.detail()?.status).toBe('validated');
    expect(component.detail()?.version).toBe(5);
  });

  it('mantém o progresso no status salvo enquanto uma nova etapa está pendente', () => {
    selecionarNoControle('validated');

    const etapasAtuais = fixture.nativeElement.querySelectorAll('[aria-current="step"]');
    expect(etapasAtuais.length).toBe(1);
    expect(etapasAtuais[0].textContent).toContain('Em execução');
    expect(fixture.nativeElement.textContent).toContain('Alteração pendente: Em execução -> Validado');
    expect(botaoComTexto('Aplicar alteração')).toBeDefined();
    expect(Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .some((button) => button.textContent?.includes('Salvar status:'))).toBeFalse();
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('prepara a etapa seguinte sem gravar e exige aplicação explícita', () => {
    component.detail.set({ ...testDetail, status: 'complete' });
    fixture.detectChanges();

    botaoComTexto('Próxima etapa').click();
    fixture.detectChanges();

    expect(component.selectedStatus()).toBe('validated');
    expect(fixture.nativeElement.querySelector('[aria-current="step"]')?.textContent).toContain('Completo');
    expect(fixture.nativeElement.textContent).toContain('Alteração pendente: Completo -> Validado');
    expect(service.updateStatus).not.toHaveBeenCalled();

    botaoComTexto('Aplicar alteração').click();

    expect(service.updateStatus).toHaveBeenCalledWith('42', {
      action: 'set-status', targetStatus: 'validated', version: 4,
    });
  });

  it('permite limpar a etapa selecionada ao escolher o status atual', () => {
    component.selectStatus('complete');
    component.selectStatus('incomplete');

    expect(component.selectedStatus()).toBeNull();
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('não oferece publicação sem confirmação de disponibilidade do laudo', () => {
    component.selectStatus('report-published');

    expect(component.selectedStatus()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('A etapa Relatório publicado só fica disponível');
    expect(fixture.nativeElement.textContent).toContain('PDF acessível ao cliente');
    expect(Array.from(fixture.nativeElement.querySelectorAll('.status-progress__label'),
      (element: Element) => element.textContent?.trim()))
      .not.toContain('Relatório publicado');
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('só oferece Relatório publicado depois de confirmar documento publicado e acessível', () => {
    component.publishedDocumentAvailable.set(true);
    fixture.detectChanges();
    component.selectStatus('report-published');

    expect(component.selectedStatus()).toBe('report-published');
    expect(fixture.nativeElement.querySelector('#new-test-status option[value="report-published"]')).not.toBeNull();
  });

  it('prepara relatório somente na etapa Elaborando relatório e navega pelo ID retornado', () => {
    const documento = { id: '91', validationStatus: 'pending-validation' as const, published: false, publishedAt: null, version: 0 };
    service.reportsForTest.and.returnValue(of({ items: [documento], publishedDocumentAvailable: false }));
    component.detail.set({ ...testDetail, status: 'drafting-report' });
    fixture.detectChanges();
    component.prepareReport();

    expect(service.prepareReport).toHaveBeenCalledWith('42');
    expect(component.reportDocuments()[0]?.id).toBe('91');
  });

  it('mostra seleção explícita quando a preparação encontra vários relatórios', () => {
    const reports = [
      { id: '91', validationStatus: 'pending-validation' as const, published: false, publishedAt: null, version: 0 },
      { id: '92', validationStatus: 'validated' as const, published: true, publishedAt: '2026-11-07T15:00:00Z', version: 2 },
    ];
    service.reportsForTest.and.returnValue(of({ items: reports, publishedDocumentAvailable: true }));
    service.prepareReport.and.returnValue(of({
      outcome: 'selection-required', reportId: null,
      reports,
    }));
    component.detail.set({ ...testDetail, status: 'drafting-report' });
    fixture.detectChanges();
    component.prepareReport();
    fixture.detectChanges();

    expect(component.reportDocuments().length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Selecione um dos documentos existentes');
    expect(fixture.nativeElement.textContent).toContain('Registro 91');
    expect(fixture.nativeElement.textContent).toContain('Registro 92');
  });

  it('mantém Relatório publicado no progresso e permite retornar para uma etapa ativa', () => {
    component.detail.set({ ...testDetail, status: 'report-published' });
    service.updateStatus.and.returnValue(of({
      id: testDetail.id, status: 'drafting-report', changedAt: '2026-11-02T15:00:00Z',
      responsible: testDetail.responsible, version: 5, changed: true,
    }));
    fixture.detectChanges();

    const statusAtual = fixture.nativeElement.querySelector('[aria-current="step"]') as HTMLElement;
    expect(statusAtual.textContent).toContain('Relatório publicado');
    expect(fixture.nativeElement.querySelector('#new-test-status option[value="report-published"]')).toBeNull();

    selecionarNoControle('drafting-report');
    expect(component.detail()?.status).toBe('report-published');
    expect(fixture.nativeElement.querySelector('[aria-current="step"]')?.textContent).toContain('Relatório publicado');
    expect(service.updateStatus).not.toHaveBeenCalled();

    botaoComTexto('Aplicar alteração').click();
    fixture.detectChanges();

    expect(service.updateStatus).toHaveBeenCalledWith('42', {
      action: 'set-status', targetStatus: 'drafting-report', version: 4,
    });
    expect(fixture.nativeElement.querySelector('[aria-current="step"]')?.textContent).toContain('Elaborando relatório');
    expect(component.saveMessage()).toContain('Os dados existentes de publicação do laudo foram preservados');
  });

  it('não permite selecionar Cancelado pelo seletor comum de etapas', () => {
    component.selectStatus('cancelled');

    expect(component.selectedStatus()).toBeNull();
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('envia rejeição com a justificativa informada no diálogo', () => {
    component.detail.set({ ...testDetail, status: 'complete' });
    dialog.open.and.returnValue({ afterClosed: () => of('Amostra insuficiente') } as never);
    service.updateStatus.and.returnValue(of({
      id: testDetail.id, status: 'incomplete', changedAt: '2026-11-01T15:00:00Z',
      responsible: testDetail.responsible, version: 5, changed: true,
    }));
    component.reject();

    expect(service.updateStatus).toHaveBeenCalledWith('42', {
      action: 'reject', justification: 'Amostra insuficiente', version: 4,
    });
  });

  it('envia cancelamento com confirmação e justificativa', () => {
    dialog.open.and.returnValue({ afterClosed: () => of('Solicitação do cliente') } as never);
    service.updateStatus.and.returnValue(of({
      id: testDetail.id, status: 'cancelled', changedAt: '2026-11-01T15:00:00Z',
      responsible: testDetail.responsible, version: 5, changed: true,
    }));
    component.cancelTest();

    expect(dialog.open).toHaveBeenCalled();
    expect(service.updateStatus).toHaveBeenCalledWith('42', {
      action: 'cancel', justification: 'Solicitação do cliente', version: 4,
    });
    expect(component.detail()?.status).toBe('cancelled');
    expect(component.canChangeStatus).toBeTrue();
  });

  it('permite reabrir ensaio cancelado com seleção direta sem justificativa', () => {
    component.detail.set({ ...testDetail, status: 'cancelled' });
    fixture.detectChanges();
    selecionarNoControle('complete');

    expect(component.selectedStatus()).toBe('complete');
    expect(fixture.nativeElement.querySelector('[aria-current="step"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Alteração pendente: Cancelado -> Completo');
    botaoComTexto('Aplicar alteração').click();

    expect(component.selectedStatus()).toBeNull();
    expect(service.updateStatus).toHaveBeenCalledWith('42', {
      action: 'set-status', targetStatus: 'complete', version: 4,
    });
    expect(component.detail()?.status).toBe('validated');
    expect(component.saveMessage()).toContain('Ensaio reaberto no status Validado');
    expect(component.saveMessage()).toContain('histórico anterior foi preservado');
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('mantém a publicação bloqueada para um ensaio cancelado', () => {
    component.detail.set({ ...testDetail, status: 'cancelled' });
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('#new-test-status') as HTMLSelectElement;
    const opcoes = Array.from(select.options).map((option) => option.value);

    expect(opcoes).not.toContain('report-published');
    expect(fixture.nativeElement.querySelector('[aria-current="step"]')).toBeNull();
    component.selectStatus('report-published');
    expect(component.selectedStatus()).toBeNull();
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('oculta rejeição e cancelamento enquanto o ensaio está cancelado', () => {
    component.detail.set({ ...testDetail, status: 'cancelled' });
    fixture.detectChanges();
    component.reject();
    component.cancelTest();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    expect(buttons.some((button) => button.textContent?.includes('Rejeitar'))).toBeFalse();
    expect(buttons.some((button) => button.textContent?.includes('Cancelar ensaio'))).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Para reabri-lo, escolha diretamente uma nova etapa');
    expect(fixture.nativeElement.textContent).toContain('o histórico de status será preservado');
    expect(component.canChangeStatus).toBeTrue();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('limpa a confirmação anterior ao preparar uma nova etapa', () => {
    component.selectStatus('validated');
    component.updateSelectedStatus();
    fixture.detectChanges();
    expect(component.saveMessage()).toContain('Status atualizado para Validado');

    selecionarNoControle('finalized');

    expect(component.saveMessage()).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Alteração pendente: Validado -> Finalizado');
    expect(fixture.nativeElement.textContent).not.toContain('Status atualizado para Validado');
  });

  it('não envia alteração redundante quando a etapa atual é selecionada', () => {
    component.detail.set({ ...testDetail, status: 'validated' });
    fixture.detectChanges();

    selecionarNoControle('validated');

    expect(component.selectedStatus()).toBeNull();
    expect(botaoComTexto('Aplicar alteração').disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).not.toContain('Alteração pendente:');
    expect(service.updateStatus).not.toHaveBeenCalled();
  });

  it('mostra rejeição com rótulo curto e mantém explicação e justificativa no diálogo', () => {
    component.detail.set({ ...testDetail, status: 'complete' });
    fixture.detectChanges();

    const rejeitar = botaoComTexto('Rejeitar');
    expect(rejeitar.textContent).not.toContain('retornar para Em execução');
    rejeitar.click();

    expect(dialog.open).toHaveBeenCalled();
  });

  it('mantém a seleção e sinaliza conflito sem sobrescrever os dados exibidos', () => {
    service.updateStatus.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { code: 'test-version-conflict' },
    })));
    component.selectStatus('complete');
    component.updateSelectedStatus();

    expect(component.conflict()).toBeTrue();
    expect(component.selectedStatus()).toBe('complete');
    expect(component.detail()?.status).toBe('incomplete');
    expect(component.saveError()).toContain('alterado em outra sessão');
  });

  it('exibe conflito de regra retornado pela API sem tratar como concorrência', () => {
    service.updateStatus.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { code: 'test-status-invalid-transition', detail: 'A etapa não está disponível no momento.' },
    })));
    component.selectStatus('complete');
    component.updateSelectedStatus();

    expect(component.conflict()).toBeFalse();
    expect(component.staleState()).toBeTrue();
    expect(component.selectedStatus()).toBe('complete');
    expect(component.detail()?.status).toBe('incomplete');
    expect(component.saveError()).toBe('A etapa não está disponível no momento.');
  });
});
