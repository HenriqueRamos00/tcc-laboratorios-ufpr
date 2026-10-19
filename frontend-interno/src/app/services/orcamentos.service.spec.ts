import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Orcamento, OrcamentoDetalhe, OrcamentoRequest } from '@/app/model/orcamento';
import { OrcamentosService } from './orcamentos.service';

describe('Serviço de orçamentos', () => {
  let service: OrcamentosService;
  let http: HttpTestingController;

  const orcamento: Orcamento = {
    id: 'quote-1',
    code: 'EAQ_2026_45482_V_1',
    name: 'Análise de óleo isolante',
    status: 'PendingAcceptance',
    stage: 'PendingAcceptance',
    description: 'Análise físico-química de óleo isolante',
    companyName: 'Copel Distribuição',
    externalContactName: 'Ana Lima',
    externalContactEmail: 'ana@example.com',
    totalPrice: 12500,
    createdDate: '2026-10-15T10:00:00Z',
    hasDocument: true,
  };

  const detalhe: OrcamentoDetalhe = {
    ...orcamento,
    companyId: 'company-1',
    laboratoryName: 'Laboratório de Ensaios',
    scope: null,
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: null, unitPrice: 12.5 }],
  };

  const requisicao: OrcamentoRequest = {
    code: 'EAQ_2026_45482_V_1',
    companyId: 'company-1',
    name: 'Análise de óleo isolante',
    laboratoryName: 'Laboratório de Ensaios',
    description: 'Análise físico-química de óleo isolante',
    scope: null,
    externalContactName: 'Ana Lima',
    externalContactEmail: 'ana@example.com',
    status: 'PendingAcceptance',
    totalPrice: 37.5,
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: null, unitPrice: 12.5 }],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OrcamentosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia busca normalizada e omite o status para Todos', () => {
    let resposta: unknown;
    service.list({ search: '  Copel  ', status: null, page: 1, pageSize: 20 })
      .subscribe((pagina) => resposta = pagina);

    const request = http.expectOne((candidate) => candidate.url === '/api/quotes');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('search')).toBe('Copel');
    expect(request.request.params.has('status')).toBeFalse();
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('pageSize')).toBe('20');
    request.flush({ items: [orcamento], page: 1, pageSize: 20, totalItems: 1 });
    expect(resposta).toEqual(jasmine.objectContaining({ totalItems: 1 }));
  });

  it('envia status e paginação em base um', () => {
    service.list({ search: '', status: 'InProgress', page: 3, pageSize: 10 }).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/quotes');

    expect(request.request.params.get('status')).toBe('InProgress');
    expect(request.request.params.get('page')).toBe('3');
    expect(request.request.params.get('pageSize')).toBe('10');
    request.flush({ items: [], page: 3, pageSize: 10, totalItems: 25 });
  });

  it('consulta detalhe e baixa o documento usando o identificador codificado', () => {
    service.get('quote/42').subscribe((resposta) => expect(resposta.id).toBe('quote/42'));
    const detailRequest = http.expectOne('/api/quotes/quote%2F42');
    detailRequest.flush({ ...detalhe, id: 'quote/42' });

    let documento: Blob | undefined;
    service.downloadDocument('quote/42').subscribe((arquivo) => documento = arquivo);
    const documentRequest = http.expectOne('/api/quotes/quote%2F42/document');
    expect(documentRequest.request.responseType).toBe('blob');
    documentRequest.flush(new Blob(['pdf'], { type: 'application/pdf' }));
    expect(documento?.type).toBe('application/pdf');
  });

  it('envia a criação como JSON quando não há PDF', () => {
    let resposta: OrcamentoDetalhe | undefined;
    service.create(requisicao).subscribe((resultado) => resposta = resultado);
    const request = http.expectOne('/api/quotes');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requisicao);
    expect(request.request.body instanceof FormData).toBeFalse();
    request.flush(detalhe, { status: 201, statusText: 'Created' });
    expect(resposta).toEqual(detalhe);
  });

  it('envia quote JSON e documento PDF como partes multipart', async () => {
    const pdf = new File(['conteúdo do PDF'], 'proposta.pdf', { type: 'application/pdf' });
    let resposta: OrcamentoDetalhe | undefined;
    service.create(requisicao, pdf).subscribe((resultado) => resposta = resultado);
    const request = http.expectOne('/api/quotes');
    const partes = request.request.body as FormData;
    const quotePart = partes.get('quote') as Blob;

    expect(request.request.method).toBe('POST');
    const documentPart = partes.get('document') as File;
    expect(documentPart.name).toBe(pdf.name);
    expect(documentPart.type).toBe('application/pdf');
    expect(quotePart.type).toBe('application/json');
    expect(JSON.parse(await quotePart.text())).toEqual(requisicao);
    expect(request.request.headers.has('Content-Type')).toBeFalse();
    request.flush(detalhe, { status: 201, statusText: 'Created' });
    expect(resposta).toEqual(detalhe);
  });

  it('atualiza sem arquivo para preservar o PDF existente no servidor', () => {
    let resposta: OrcamentoDetalhe | undefined;
    service.update('quote-1', requisicao).subscribe((resultado) => resposta = resultado);
    const request = http.expectOne('/api/quotes/quote-1');

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(requisicao);
    expect(request.request.body instanceof FormData).toBeFalse();
    request.flush(detalhe);
    expect(resposta).toEqual(detalhe);
  });
});
