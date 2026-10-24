import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DashboardAdministrador } from '@/app/model/dashboard-administrador';
import { DashboardAdministradorService } from './dashboard-administrador.service';

describe('Serviço do dashboard do administrador', () => {
  let service: DashboardAdministradorService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardAdministradorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta os indicadores na rota administrativa', () => {
    const esperado: DashboardAdministrador = {
      totalQuotes: 125,
      pendingAcceptance: 8,
      activeClients: 42,
      totalQuotedValue: 1501.5,
    };
    let resposta: DashboardAdministrador | undefined;

    service.get().subscribe((indicadores) => resposta = indicadores);

    const request = http.expectOne('/api/admin/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush(esperado);
    expect(resposta).toEqual(esperado);
  });

  it('propaga a falha da API sem fabricar indicadores', () => {
    let status = 0;

    service.get().subscribe({ error: (error) => status = error.status });

    http.expectOne('/api/admin/dashboard').flush(
      { message: 'Falha na consulta' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(status).toBe(503);
  });

  it('propaga a resposta 403 sem convertê-la em indicadores zerados', () => {
    let status = 0;

    service.get().subscribe({ error: (error) => status = error.status });

    http.expectOne('/api/admin/dashboard').flush(
      { message: 'Sem permissão' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(status).toBe(403);
  });
});
