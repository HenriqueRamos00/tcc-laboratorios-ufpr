import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import {
  MeasurementTemplate,
  SampleReference,
  TestDetail,
} from '@/app/model/ensaio';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { EnsaioFormComponent } from './ensaio-form.component';

const sample: SampleReference = {
  id: '31',
  code: 'A-HU25-0001/2026',
  description: 'Óleo do transformador TR-01',
  quote: {
    id: '8',
    code: 'DEMO-HU25-001',
    companyId: '4',
    companyName: 'Empresa de demonstração',
  },
  analyzedEquipment: null,
};
const saved: TestDetail = {
  id: '42',
  code: 'E-42/2026',
  sample: { id: sample.id, code: sample.code, description: sample.description },
  quote: {
    id: '8',
    code: 'DEMO-HU25-001',
    companyId: '4',
    companyName: 'Empresa de demonstração',
  },
  type: 'Físico-químico',
  analysisType: 'physical-chemical',
  scheduledDate: '2026-10-26',
  responsible: {
    id: '7',
    name: 'Técnica responsável',
    unit: 'Laboratório',
    active: true,
  },
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
      'samples',
      'technicians',
      'instruments',
      'get',
      'template',
      'create',
      'update',
    ]);
    service.samples.and.returnValue(
      of({ items: [sample], page: 1, pageSize: 20, totalItems: 1 }),
    );
    service.technicians.and.returnValue(
      of([
        {
          id: '7',
          name: 'Técnica responsável',
          unit: 'Laboratório',
          active: true,
        },
      ]),
    );
    service.instruments.and.returnValue(of([]));
    service.get.and.returnValue(of(saved));
    const physicalMeasurements = [
      [
        'neutralizacao',
        'Índice de neutralização',
        'mg KOH/g óleo',
        'NBR 14248',
      ],
      ['agua', 'Teor de água', 'ppm m/m', 'NBR 10710'],
      ['densidade', 'Densidade a 20/4 °C', 'g/mL', 'NBR 14065'],
      ['fator-potencia', 'Fator de potência a 100 °C', '%', 'NBR 12133'],
      ['rigidez', 'Rigidez dielétrica - Colota', 'kV', 'NBR/IEC 60156'],
      ['tensao-interfacial', 'Tensão interfacial', 'dina/cm', 'NBR 6234'],
    ].map(([code, name, unit, method]) => ({
      code,
      name,
      unit,
      method,
      referenceLimit: null,
    }));
    const chromatographyMeasurements = [
      ['h2', 'Hidrogênio'],
      ['o2', 'Oxigênio'],
      ['n2', 'Nitrogênio'],
      ['ch4', 'Metano'],
      ['co', 'Monóxido de carbono'],
      ['co2', 'Dióxido de carbono'],
      ['c2h4', 'Etileno'],
      ['c2h6', 'Etano'],
      ['c2h2', 'Acetileno'],
      ['combustiveis', 'Total de gases combustíveis'],
      ['gases', 'Total de gases'],
    ].map(([code, name]) => ({
      code,
      name,
      unit: 'ppm',
      method: null,
      referenceLimit: null,
    }));
    service.template.and.callFake((type) =>
      of({
        analysisType: type,
        measurements:
          type === 'chromatography'
            ? chromatographyMeasurements
            : physicalMeasurements,
      }),
    );
    service.create.and.returnValue(of(saved));
    await TestBed.configureTestingModule({
      imports: [EnsaioFormComponent],
      providers: [
        provideRouter([]),
        { provide: EnsaiosService, useValue: service },
        {
          provide: AppDialogService,
          useValue: jasmine.createSpyObj('AppDialogService', ['open']),
        },
      ],
    }).compileComponents();
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    dialog = TestBed.inject(
      AppDialogService,
    ) as jasmine.SpyObj<AppDialogService>;
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as never);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(EnsaioFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('preserva o texto técnico e envia referências por ID ao criar', fakeAsync(() => {
    component.sampleSearch.setValue('amostra');
    tick(300);
    const resultInput = (
      fixture.nativeElement as HTMLElement
    ).querySelector<HTMLInputElement>('input[formControlName="resultInput"]');
    expect(resultInput).not.toBeNull();
    resultInput!.value = '0,01%';
    resultInput!.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(component.measurements.at(0).get('resultInput')?.value).toBe(
      '0,01%',
    );
    component.samples.set([sample]);
    component.sampleTotal.set(1);
    component.form.patchValue({
      sampleId: sample.id,
      type: 'Físico-químico',
      analysisType: 'physical-chemical',
      method: 'Método de ensaio',
      standard: 'Norma técnica',
      scheduledDate: '2026-10-26',
      responsibleId: '7',
      reportText: 'Observação técnica\n  segunda linha  ',
    });
    expect(component.hasReferences).toBeTrue();
    expect(component.form.valid).toBeTrue();
    component.save();

    expect(service.create).toHaveBeenCalled();
    const request = service.create.calls.mostRecent().args[0];
    expect(request).toEqual(
      jasmine.objectContaining({
        sampleId: '31',
        responsibleId: '7',
        reportText: 'Observação técnica\n  segunda linha  ',
      }),
    );
    expect(request.measurements).toHaveSize(6);
    expect(request.measurements).toContain(
      jasmine.objectContaining({
        code: 'neutralizacao',
        result: { kind: 'text', text: '0,01%' },
      }),
    );
  }));

  it('renderiza as seis linhas físico-químicas e as onze cromatográficas no formulário', () => {
    expect(component.rows.length).toBe(6);
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr')
        .length,
    ).toBe(6);

    component.changeAnalysisType('chromatography');
    fixture.detectChanges();

    expect(component.rows.length).toBe(11);
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr')
        .length,
    ).toBe(11);
  });

  it('preserva linhas editadas quando a troca de modelo é cancelada', () => {
    component.measurements.at(0).get('method')?.setValue('Método alterado');
    component.measurements.markAsDirty();
    component.form.controls.analysisType.setValue('chromatography');
    component.changeAnalysisType('chromatography');

    expect(dialog.open).toHaveBeenCalled();
    expect(component.form.controls.analysisType.value).toBe(
      'physical-chemical',
    );
    expect(component.measurements.length).toBe(6);
    expect(component.measurements.at(0).get('method')?.value).toBe(
      'Método alterado',
    );
  });

  it('preserva os dados digitados quando a API informa conflito de versão', fakeAsync(() => {
    component.samples.set([sample]);
    component.sampleTotal.set(1);
    component.detail.set(saved);
    component.editId.set(saved.id);
    component.form.patchValue({
      sampleId: sample.id,
      type: 'Alteração ainda não salva',
      method: 'Método de ensaio',
      standard: 'Norma técnica',
      scheduledDate: '2026-10-26',
      responsibleId: '7',
      analysisClassification: null,
      reportText: 'Rascunho local',
    });
    service.update.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    component.save();

    expect(component.conflict()).toBeTrue();
    expect(component.form.controls.type.value).toBe(
      'Alteração ainda não salva',
    );
    expect(component.form.controls.reportText.value).toBe('Rascunho local');
  }));

  it('foca o primeiro campo obrigatório quando a validação falha', fakeAsync(() => {
    component.sampleSearch.setValue('amostra');
    tick(300);
    component.save();
    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('#sample-id'),
    );
  }));

  it('impede sair da tela durante o envio e libera a navegação depois', () => {
    component.saving.set(true);
    expect(component.canDeactivate()).toBeFalse();
    component.saving.set(false);
    expect(component.canDeactivate()).toBeTrue();
  });

  it('cancela o modelo pendente ao voltar para o formato já carregado', () => {
    const response = new Subject<{
      analysisType: string;
      measurements: MeasurementTemplate[];
    }>();
    service.template.and.returnValue(response);

    component.changeAnalysisType('chromatography');
    expect(component.templateLoading()).toBeTrue();

    component.changeAnalysisType('physical-chemical');
    response.next({ analysisType: 'chromatography', measurements: [] });
    response.complete();
    fixture.detectChanges();

    expect(component.templateLoading()).toBeFalse();
    expect(component.form.controls.analysisType.value).toBe(
      'physical-chemical',
    );
    expect(component.rows.length).toBe(6);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(6);
  });

  it('aguarda o modelo de resultados antes de enviar um formulário válido', () => {
    const response = new Subject<{
      analysisType: string;
      measurements: MeasurementTemplate[];
    }>();
    service.template.and.returnValue(response);
    component.samples.set([sample]);
    component.sampleTotal.set(1);
    component.form.patchValue({
      sampleId: sample.id,
      type: 'Cromatografia',
      method: 'Método de ensaio',
      standard: 'Norma técnica',
      scheduledDate: '2026-10-26',
      responsibleId: '7',
    });
    component.changeAnalysisType('chromatography');

    component.save();

    expect(service.create).not.toHaveBeenCalled();
    expect(component.saveError()).toContain('Aguarde o carregamento');
    response.complete();
  });
});
