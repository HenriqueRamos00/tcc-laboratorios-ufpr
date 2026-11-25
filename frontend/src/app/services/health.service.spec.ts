import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import type { HealthIndicators } from '@/app/model/health';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';

import { HealthService } from './health.service';

describe('HealthService', () => {
  let service: HealthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HealthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('entrega os laudos de cada aba como vieram, com rótulo e conformidade', () => {
    let received: HealthIndicators | undefined;

    service.equipmentIndicators('eq/1').subscribe((body) => (received = body));
    http.expectOne(`${API_URL}/api/equipments/eq%2F1/health`).flush(HEALTH_INDICATORS);

    expect(received?.dissolvedGases.reports.map((report) => report.resultLabel))
      .toEqual(['NORMAL', 'Descarga elétrica']);
    expect(received?.dissolvedGases.conformity).toBe('normal');
    expect(received?.physicalChemical.reports[0].identification).toBe('LACTEC-04809/2025');
  });

  it('pede o laudo escolhido pela rota de relatório', () => {
    service.equipmentIndicators('eq', 'rel/2').subscribe();

    http.expectOne(`${API_URL}/api/equipments/eq/health/reports/rel%2F2`).flush(HEALTH_INDICATORS);
  });
});
