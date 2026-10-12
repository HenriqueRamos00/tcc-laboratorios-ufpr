import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Cliente } from '@/app/model/cliente';
import { ClientesService } from './clientes.service';

describe('Serviço de clientes', () => {
  let service: ClientesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ClientesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia busca e status combinados para a API Spring', () => {
    service.list('  Copel  ', 'INACTIVE').subscribe();
    const request = http.expectOne((req) =>
      req.url === '/api/clients' && req.params.get('search') === 'Copel' && req.params.get('status') === 'INACTIVE',
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('omite o status quando a tela solicita Todos', () => {
    service.list('', null).subscribe();
    const request = http.expectOne('/api/clients');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush([]);
  });

  it('cria cliente com empresas e senha, atualiza dados e envia situação por PATCH', () => {
    const entrada = {
      name: 'Ana Lima', email: 'ana@example.com', phone: '(41) 3333-4444', position: 'Compradora',
      status: 'ACTIVE' as const, companyIds: ['empresa-1', 'empresa-2'], password: '  senha inicial  ',
    };
    const resposta: Cliente = {
      id: 'cliente-1', name: entrada.name, email: entrada.email, phone: entrada.phone, position: entrada.position,
      status: 'ACTIVE', companies: [{ id: 'empresa-1', name: 'Empresa Um' }, { id: 'empresa-2', name: 'Empresa Dois' }],
    };
    service.create(entrada).subscribe();
    const createRequest = http.expectOne('/api/clients');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(entrada);
    createRequest.flush(resposta);

    const edicao = { ...entrada, password: undefined };
    service.update('cliente-1', edicao).subscribe();
    const updateRequest = http.expectOne('/api/clients/cliente-1');
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body.companyIds).toEqual(['empresa-1', 'empresa-2']);
    expect(updateRequest.request.body.password).toBeUndefined();
    updateRequest.flush(resposta);

    service.changeStatus('cliente-1', 'INACTIVE').subscribe();
    const statusRequest = http.expectOne('/api/clients/cliente-1/status');
    expect(statusRequest.request.method).toBe('PATCH');
    expect(statusRequest.request.body).toEqual({ status: 'INACTIVE' });
    statusRequest.flush({ ...resposta, status: 'INACTIVE' });
  });

  it('propaga respostas de erro sem converter falha em lista vazia', () => {
    let status = 0;
    service.list('', null).subscribe({ error: (error) => status = error.status });
    http.expectOne('/api/clients').flush({ message: 'Falha no serviço' }, { status: 503, statusText: 'Service Unavailable' });
    expect(status).toBe(503);
  });
});
