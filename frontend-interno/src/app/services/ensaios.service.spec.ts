import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EnsaiosService } from './ensaios.service';

describe('Serviço de ensaios', () => {
  let service: EnsaiosService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EnsaiosService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('consulta a página de ensaios com termo de busca normalizado', () => {
    service.list('  óleo  ', 2, 20).subscribe();
    const request = http.expectOne(
      (item) =>
        item.url === '/api/tests' &&
        item.params.get('search') === 'óleo' &&
        item.params.get('page') === '2',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], page: 2, pageSize: 20, totalItems: 0 });
  });
  it('consulta amostras paginadas por pesquisa na rota própria de referências', () => {
    service.samples('TR-01', 3, 20).subscribe();
    const request = http.expectOne(
      (item) =>
        item.url === '/api/tests/references/samples' &&
        item.params.get('search') === 'TR-01' &&
        item.params.get('page') === '3',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], page: 3, pageSize: 20, totalItems: 0 });
  });
  it('envia atualizações para o recurso escolhido pelo ID', () => {
    service.update('42', { version: 3 } as never).subscribe();
    const request = http.expectOne('/api/tests/42');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(3);
    request.flush({});
  });
});
