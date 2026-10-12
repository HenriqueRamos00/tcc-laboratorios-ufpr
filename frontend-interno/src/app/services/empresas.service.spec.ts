import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Empresa } from '@/app/model/empresa';
import { EmpresasService } from './empresas.service';

describe('Serviço de empresas', () => {
  let service: EmpresasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EmpresasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('normaliza a busca e omite o parâmetro de status para Todos', () => {
    let resposta: Empresa[] | undefined;
    service.list('  Energia  ', null).subscribe((empresas) => resposta = empresas);

    const request = http.expectOne('/api/companies?search=Energia');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush([]);
    expect(resposta).toEqual([]);
  });

  it('envia o filtro de status ativo para a API', () => {
    service.list('', 'ACTIVE').subscribe();
    const request = http.expectOne('/api/companies?status=ACTIVE');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('cria, edita e altera a situação usando os contratos da API Spring', () => {
    const entrada = {
      legalName: 'Empresa Local', cnpj: '04368898000106', address: 'Rua Um, 10', city: 'Curitiba', state: 'PR',
      contactName: 'Ana Lima', contactPhone: '4133334444', contactEmail: 'ana@example.com', status: 'ACTIVE' as const,
    };
    const resposta: Empresa = { ...entrada, id: 'empresa-local-1', origin: 'LOCAL' };
    service.create(entrada).subscribe();
    const createRequest = http.expectOne('/api/companies');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(entrada);
    createRequest.flush(resposta);

    service.update('empresa-local-1', entrada).subscribe();
    const updateRequest = http.expectOne('/api/companies/empresa-local-1');
    expect(updateRequest.request.method).toBe('PUT');
    updateRequest.flush(resposta);

    service.changeStatus('empresa-local-1', 'INACTIVE').subscribe();
    const statusRequest = http.expectOne('/api/companies/empresa-local-1/status');
    expect(statusRequest.request.method).toBe('PATCH');
    expect(statusRequest.request.body).toEqual({ status: 'INACTIVE' });
    statusRequest.flush({ ...resposta, status: 'INACTIVE' });
  });

  it('propaga falhas de permissão para a tela', () => {
    let status = 0;
    service.list('', null).subscribe({ error: (error) => status = error.status });
    http.expectOne('/api/companies').flush({ message: 'Sem permissão' }, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });
});
