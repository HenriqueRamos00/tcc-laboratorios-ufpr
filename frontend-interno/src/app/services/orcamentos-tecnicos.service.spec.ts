import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OrcamentoTecnicoDetalhe, OrcamentoTecnico } from '@/app/model/orcamento-tecnico';
import { OrcamentosTecnicosService } from './orcamentos-tecnicos.service';

describe('Serviço de orçamentos técnicos', () => {
  let service: OrcamentosTecnicosService;
  let http: HttpTestingController;

  const orcamento: OrcamentoTecnico = {
    id: 'quote-1',
    code: 'EAQ_2026_33322_V_3',
    name: 'Análise de óleo isolante',
    companyName: 'Copel Distribuição',
    createdDate: '2026-10-15T10:00:00-03:00',
    technicalStatus: 'validated',
  };

  const detalhe: OrcamentoTecnicoDetalhe = {
    ...orcamento,
    description: 'Análise físico-química de óleo isolante',
    laboratoryName: 'Laboratório de Materiais',
    scope: 'Caracterização de óleo isolante',
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533' }],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OrcamentosTecnicosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia filtros técnicos e paginação ao endpoint técnico', () => {
    let resposta: unknown;
    service.list({
      search: '  Copel  ',
      status: 'validated',
      from: '2026-10-01',
      to: '2026-10-31',
      page: 2,
      pageSize: 20,
    }).subscribe((pagina) => resposta = pagina);

    const request = http.expectOne((candidate) => candidate.url === '/api/technician/quotes');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('search')).toBe('Copel');
    expect(request.request.params.get('status')).toBe('validated');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-31');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('pageSize')).toBe('20');
    request.flush({ items: [orcamento], page: 2, pageSize: 20, totalItems: 21 });
    expect(resposta).toEqual(jasmine.objectContaining({ totalItems: 21 }));
  });

  it('omite filtros opcionais ausentes sem incluir status comerciais', () => {
    service.list({
      search: '',
      status: null,
      from: '2026-10-01',
      to: null,
      page: 1,
      pageSize: 20,
    }).subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/api/technician/quotes');
    expect(request.request.params.has('search')).toBeFalse();
    expect(request.request.params.has('status')).toBeFalse();
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.has('to')).toBeFalse();
    request.flush({ items: [], page: 1, pageSize: 20, totalItems: 0 });
  });

  it('consulta o detalhe técnico com o identificador codificado', () => {
    let resultado: OrcamentoTecnicoDetalhe | undefined;
    service.get('quote/42').subscribe((orcamentoDetalhe) => resultado = orcamentoDetalhe);
    const request = http.expectOne('/api/technician/quotes/quote%2F42');

    expect(request.request.method).toBe('GET');
    request.flush({ ...detalhe, id: 'quote/42' });
    expect(resultado?.items).toEqual(detalhe.items);
  });
});
