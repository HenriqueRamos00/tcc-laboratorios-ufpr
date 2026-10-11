import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { HealthService } from './health.service';
import { EquipmentsService } from './equipments.service';

describe('Endereços de equipamentos e indicadores da API C#', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta listagem, detalhe e histórico sem duplicar o prefixo /api', () => {
    const equipments = TestBed.inject(EquipmentsService);
    equipments.list().subscribe();
    const list = http.expectOne('/api/equipments');
    expect(list.request.method).toBe('GET');
    list.flush([]);
    equipments.findById('equip/1').subscribe();
    http.expectOne('/api/equipments/equip%2F1').flush({});
    equipments.reportHistory('equip/1').subscribe();
    http.expectOne('/api/equipments/equip%2F1/reports').flush([]);
  });

  it('consulta a visão geral e a análise selecionada preservando identificadores opacos', () => {
    const health = TestBed.inject(HealthService);
    health.equipmentIndicators('equip/1').subscribe();
    const overview = http.expectOne('/api/equipments/equip%2F1/health');
    expect(overview.request.method).toBe('GET');
    overview.flush({});
    health.equipmentIndicators('equip/1', 'laudo/2').subscribe();
    http.expectOne('/api/equipments/equip%2F1/health/reports/laudo%2F2').flush({});
  });
});
