import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Tecnico } from '@/app/model/tecnico';
import { TecnicosService } from './tecnicos.service';

describe('Serviço de técnicos', () => {
  let service: TecnicosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TecnicosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('normaliza a busca e omite o parâmetro de status para Todos', () => {
    let response: Tecnico[] | undefined;
    service.list('  maria  ', null).subscribe((tecnicos) => response = tecnicos);

    const request = http.expectOne('/api/technicians?search=maria');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush([]);
    expect(response).toEqual([]);
  });

  it('combina os filtros de busca e status', () => {
    service.list('DOLEO', 'INACTIVE').subscribe();
    const request = http.expectOne((req) =>
      req.url === '/api/technicians' && req.params.get('search') === 'DOLEO' && req.params.get('status') === 'INACTIVE',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('status')).toBe('INACTIVE');
    request.flush([]);
  });

  it('propaga falhas da API para a página em vez de convertê-las em lista vazia', () => {
    let status = 0;
    service.list('', null).subscribe({ error: (error) => status = error.status });
    http.expectOne('/api/technicians').flush({ message: 'Sem permissão' }, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });
});
