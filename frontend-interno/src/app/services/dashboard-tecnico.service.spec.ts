import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DashboardTecnico } from '@/app/model/dashboard-tecnico';
import { DashboardTecnicoService } from './dashboard-tecnico.service';

describe('Serviço do dashboard técnico', () => {
  let service: DashboardTecnicoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardTecnicoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta o contrato do dashboard técnico sem agregar dados no navegador', () => {
    const esperado: DashboardTecnico = {
      testsInExecution: 3,
      testsAwaitingApproval: 2,
      testsFinished: 5,
      pendingReportsTotal: 7,
      pendingReports: [
        {
          id: '31',
          companyName: 'Empresa de Energia',
          type: 'Cromatografia de gases dissolvidos',
          validationStatus: 'pending-validation',
        },
      ],
    };
    let resposta: DashboardTecnico | undefined;

    service.get().subscribe((dashboard) => resposta = dashboard);

    const request = http.expectOne('/api/technician/dashboard');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(esperado);
    expect(resposta).toEqual(esperado);
  });

  it('propaga falhas da API sem fabricar valores de dashboard', () => {
    let status = 0;

    service.get().subscribe({ error: (error) => status = error.status });

    http.expectOne('/api/technician/dashboard').flush(
      { message: 'Falha na consulta' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(status).toBe(503);
  });
});
