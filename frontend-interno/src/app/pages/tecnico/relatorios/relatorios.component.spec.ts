import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { BehaviorSubject } from 'rxjs';

import { Page } from '@/app/model/ensaio';
import {
  ReportDetail,
  ReportPublicationResponse,
  ReportSummary,
  ReportValidationResponse,
} from '@/app/model/relatorio';
import { RelatoriosService } from '@/app/services/relatorios.service';
import { RelatoriosComponent } from './relatorios.component';

const report: ReportSummary = {
  id: 'LCT-2026-0045',
  company: { id: '31', name: 'Copel Distribuição' },
  quote: { id: '512', code: 'ORC-2026-0091' },
  sample: { id: '702', code: 'A-1208/2026', description: 'Óleo isolante do transformador' },
  type: 'Análise físico-química',
  analysisType: 'physical-chemical',
  validationStatus: 'pending-validation',
  published: false,
  pdfAvailable: true,
  createdAt: '2026-11-07T14:30:00Z',
  validatedAt: null,
  publishedAt: null,
  version: 4,
};

const detail: ReportDetail = {
  ...report,
  contentFingerprint: '0000000000000000000000000000000000000000000000000000000000000000',
  validatedBy: null,
  publishedBy: null,
  test: {
    id: '699',
    code: 'EN-2026-0198',
    type: report.type,
    analysisType: 'physical-chemical',
    method: 'Cromatografia gasosa',
    standard: 'ABNT NBR 7274',
    scheduledDate: '2026-11-05',
    technicalStatus: 'incomplete',
    responsible: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
    analyzedEquipment: null,
    instruments: [],
    analysisClassification: null,
    reportText: null,
    measurements: [{
      code: 'water-content',
      name: 'Teor de água',
      unit: 'mg/kg',
      method: 'Karl Fischer',
      referenceLimit: '35 mg/kg',
      result: { kind: 'quantity', value: '12.3400' },
    }],
  },
};

const validatedReport: ReportSummary = {
  ...report,
  validationStatus: 'validated',
  validatedAt: '2026-11-07T15:04:12Z',
  version: 5,
};

const validatedDetail: ReportDetail = {
  ...detail,
  ...validatedReport,
  validatedBy: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
  publishedBy: null,
};

describe('Consulta de relatórios técnicos', () => {
  let fixture: ComponentFixture<RelatoriosComponent>;
  let component: RelatoriosComponent;
  let service: jasmine.SpyObj<RelatoriosService>;
  let activeReportId: string | null = null;
  let routeParams: BehaviorSubject<ParamMap>;
  const activatedRoute = {
    get snapshot() {
      return {
        paramMap: convertToParamMap(activeReportId ? { id: activeReportId } : {}),
      };
    },
    get paramMap() { return routeParams.asObservable(); },
  };

  beforeEach(async () => {
    activeReportId = null;
    routeParams = new BehaviorSubject(convertToParamMap({}));
    service = jasmine.createSpyObj<RelatoriosService>('RelatoriosService', [
      'list', 'get', 'validate', 'publish', 'pdf',
    ]);
    service.list.and.returnValue(of({
      items: [report], page: 1, pageSize: 20, totalItems: 1,
    } satisfies Page<ReportSummary>));
    service.get.and.returnValue(of(detail));
    service.validate.and.returnValue(of({
      id: report.id,
      validationStatus: 'validated',
      published: false,
      validatedAt: '2026-11-07T15:04:12Z',
      validatedBy: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
      publishedAt: null,
      version: 5,
      pdfAvailable: true,
    }));
    service.publish.and.returnValue(of({
      id: report.id, validationStatus: 'validated', published: true,
      publishedAt: '2026-11-07T16:10:00Z',
      publishedBy: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
      version: 6, pdfAvailable: true,
    } satisfies ReportPublicationResponse));

    await TestBed.configureTestingModule({
      imports: [RelatoriosComponent],
      providers: [
        provideRouter([{ path: 'tecnico', children: [
          { path: 'relatorios/:id', children: [] },
        ] }]),
        { provide: ActivatedRoute, useValue: activatedRoute },
        { provide: RelatoriosService, useValue: service },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RelatoriosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('apresenta dados persistidos e separa status documental de status do ensaio', () => {
    const texto = fixture.nativeElement.textContent as string;

    expect(service.list).toHaveBeenCalledWith({
      search: '', validationStatus: null, published: null, page: 1, pageSize: 20,
    });
    expect(service.get).toHaveBeenCalledWith(report.id);
    expect(texto).toContain('Aguardando validação');
    expect(texto).toContain('Não publicado');
    expect(texto).toContain('Em execução');
    expect(texto).toContain('12.3400');
    expect(texto).toContain('Registro criado');
    expect(texto).toContain('Ainda não ocorreu');
  });

  it('mantém título e breadcrumb do fluxo documental consolidados', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Relatórios');
    expect(fixture.nativeElement.textContent).toContain('Pesquisar relatório');
    expect(component.pageTitle).toBe('Relatórios');
    expect(component.breadcrumbLabel).toBe('Pesquisar relatório');
  });

  it('navega para a rota canônica ao selecionar um relatório', () => {
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.resolveTo(true);

    component.selectReport(report);

    expect(navegar).toHaveBeenCalledWith(['/tecnico/relatorios', report.id]);
  });

  it('valida com a versão carregada e mantém a publicação separada', () => {
    service.list.and.returnValues(
      of({ items: [report], page: 1, pageSize: 20, totalItems: 1 }),
      of({ items: [validatedReport], page: 1, pageSize: 20, totalItems: 1 }),
    );
    service.get.and.returnValues(of(detail), of(validatedDetail));
    fixture.destroy();

    fixture = TestBed.createComponent(RelatoriosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.validateReport();
    fixture.detectChanges();

    expect(service.validate).toHaveBeenCalledWith(
      report.id,
      report.version,
      detail.contentFingerprint,
    );
    expect(component.detail()?.validationStatus).toBe('validated');
    expect(component.detail()?.published).toBeFalse();
    expect(component.detail()?.validatedBy?.name).toBe('Técnica Exemplo');
    expect(fixture.nativeElement.textContent).toContain('Relatório validado.');
    expect(fixture.nativeElement.textContent).toContain('Não publicado');
  });

  it('publica somente o relatório validado com a versão atual e mostra autoria real', () => {
    const publishedSummary = {
      ...validatedReport,
      published: true,
      publishedAt: '2026-11-07T16:10:00Z',
      version: 6,
    } satisfies ReportSummary;
    const publishedDetail = {
      ...validatedDetail,
      ...publishedSummary,
      publishedBy: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
    } satisfies ReportDetail;
    service.publish.and.returnValue(of({
      id: report.id, validationStatus: 'validated', published: true,
      publishedAt: '2026-11-07T16:10:00Z',
      publishedBy: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
      version: 6, pdfAvailable: true,
    }));
    service.get.and.returnValue(of(publishedDetail));
    service.list.and.returnValue(of({ items: [publishedSummary], page: 1, pageSize: 20, totalItems: 1 }));
    component.detail.set(validatedDetail);
    component.selectedId.set(report.id);
    fixture.detectChanges();

    component.publishReport();
    fixture.detectChanges();

    expect(service.publish).toHaveBeenCalledWith(report.id, validatedReport.version);
    expect(component.detail()?.published).toBeTrue();
    expect(component.detail()?.publishedBy?.name).toBe('Técnica Exemplo');
    expect(fixture.nativeElement.textContent).toContain('Relatório publicado para o cliente');
    expect(fixture.nativeElement.textContent).toContain('Publicado por');
    expect(fixture.nativeElement.textContent).toContain('status técnico do ensaio continua manual');
  });

  it('mantém o documento sem publicação quando a versão diverge', () => {
    service.publish.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.detail.set(validatedDetail);

    component.publishReport();

    expect(service.publish).toHaveBeenCalledWith(report.id, validatedReport.version);
    expect(component.detail()?.published).toBeFalse();
    expect(component.publicationConflict()).toBeTrue();
  });

  it('abre o relatório solicitado por ID mesmo quando ele não está na página da lista', () => {
    fixture.destroy();
    activeReportId = '999';
    routeParams = new BehaviorSubject(convertToParamMap({ id: activeReportId }));
    service.get.and.returnValue(of({ ...detail, id: activeReportId }));

    fixture = TestBed.createComponent(RelatoriosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.selectedId()).toBe('999');
    expect(component.detail()?.id).toBe('999');
    expect(service.get).toHaveBeenCalledWith('999');
  });

  it('cancela o detalhe anterior e carrega o novo ID de rota durante uma consulta', () => {
    const respostaA = new Subject<ReportDetail>();
    const respostaB = new Subject<ReportDetail>();
    service.get.and.callFake((id) => id === 'A' ? respostaA : respostaB);

    fixture.destroy();
    activeReportId = 'A';
    routeParams = new BehaviorSubject(convertToParamMap({ id: 'A' }));
    fixture = TestBed.createComponent(RelatoriosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(service.get).toHaveBeenCalledWith('A');
    expect(component.detailLoading()).toBeTrue();

    routeParams.next(convertToParamMap({ id: 'B' }));
    expect(component.selectedId()).toBe('B');
    expect(service.get).toHaveBeenCalledWith('B');

    respostaA.next({ ...detail, id: 'A' });
    expect(component.detail()).toBeNull();
    respostaB.next({ ...detail, id: 'B' });

    expect(component.detail()?.id).toBe('B');
    expect(component.selectedId()).toBe('B');
  });

  it('preserva o relatório selecionado ao voltar da rota direta para a lista', () => {
    const other = { ...report, id: '2' };
    service.list.and.returnValue(of({ items: [report, other], page: 1, pageSize: 20, totalItems: 2 }));
    service.get.and.callFake((id) => of({ ...detail, id }));

    fixture.destroy();
    fixture = TestBed.createComponent(RelatoriosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.selectReport(other);
    routeParams.next(convertToParamMap({ id: other.id }));
    routeParams.next(convertToParamMap({}));

    expect(component.selectedId()).toBe(other.id);
    expect(component.detail()?.id).toBe(other.id);
  });

  it('mantém o estado e pede recarga quando a versão entra em conflito', () => {
    service.validate.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    component.validateReport();
    fixture.detectChanges();

    expect(component.detail()?.validationStatus).toBe('pending-validation');
    expect(component.validationConflict()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Recarregue os dados');
    expect(component.validationMessage()).toBeNull();
  });

  it('preserva a seleção quando a validação de outro relatório termina', () => {
    const pending = new Subject<ReportValidationResponse>();
    service.validate.and.returnValue(pending);
    component.validateReport();

    const other = { ...report, id: '2' };
    service.list.and.returnValue(of({ items: [report, other], page: 1, pageSize: 20, totalItems: 2 }));
    service.get.and.callFake((id) => of(id === other.id ? { ...detail, id: other.id } : validatedDetail));
    component.selectReport(other);
    pending.next({ id: report.id, validationStatus: 'validated', published: false,
      validatedAt: '2026-11-07T15:04:12Z',
      validatedBy: { id: '12', name: 'Técnica Exemplo', unit: 'Química' },
      publishedAt: null, version: 5, pdfAvailable: true });
    pending.complete();

    expect(component.selectedId()).toBe(other.id);
    expect(component.detail()?.id).toBe(other.id);
    expect(component.validationMessage()).toBeNull();
    expect(component.saving()).toBeFalse();
  });

  it('não apresenta conflito de outro relatório após trocar a seleção', () => {
    const pending = new Subject<never>();
    service.validate.and.returnValue(pending);
    component.validateReport();
    service.get.and.returnValue(of({ ...detail, id: '2' }));
    component.selectReport({ ...report, id: '2' });
    pending.error(new HttpErrorResponse({ status: 409 }));

    expect(component.detail()?.id).toBe('2');
    expect(component.validationConflict()).toBeFalse();
    expect(component.validationError()).toBeNull();
    expect(component.saving()).toBeFalse();
  });

});
