import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import { QuotesService } from './quotes.service';

describe('Serviço de orçamentos', () => {
  let service: QuotesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(QuotesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('retorna a lista recebida da API sem alterar o contrato', () => {
    const quotes = [{
      id: 'orc-1',
      code: 'EAQ-1',
      name: 'Análise de óleo',
      status: 'Em negociação',
      stage: 'Em negociação',
      description: 'Óleo isolante',
      companyName: 'Empresa vinculada',
      externalContactName: '',
      externalContactEmail: '',
      totalPrice: 1200,
      createdDate: null,
    }];
    let returned: unknown;

    service.getQuotes().subscribe((response) => returned = response);
    http.expectOne(`${API_URL}/client/quotes`).flush(quotes);

    expect(returned).toEqual(quotes);
  });

  it('propaga indisponibilidade da API sem substituir por dados locais', () => {
    let status: number | undefined;
    service.getQuotes().subscribe({ error: (error) => status = error.status });
    http.expectOne(`${API_URL}/client/quotes`).flush('API indisponível', {
      status: 503,
      statusText: 'Serviço indisponível',
    });

    expect(status).toBe(503);
  });

  it('rejeita envelopes incompatíveis sem apresentá-los como lista vazia', () => {
    let errorMessage = '';
    service.getQuotes().subscribe({ error: (error: Error) => errorMessage = error.message });
    http.expectOne(`${API_URL}/client/quotes`).flush({ items: [] });

    expect(errorMessage).toBe('A resposta de orçamentos precisa ser uma lista.');
  });

  it('baixa o documento com o endpoint autenticado do orçamento', () => {
    let returned: Blob | undefined;
    service.getQuoteDocument('orc/1').subscribe((response) => returned = response);
    const file = new Blob(['%PDF-1.7'], { type: 'application/pdf' });
    const request = http.expectOne(`${API_URL}/client/quotes/orc%2F1/document`);
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(file);

    expect(returned?.type).toBe('application/pdf');
    expect(returned?.size).toBe(file.size);
  });

  it('propaga a falha retornada ao tentar baixar o PDF', () => {
    let status: number | undefined;
    service.getQuoteDocument('orc-1').subscribe({ error: (error) => status = error.status });
    http.expectOne(`${API_URL}/client/quotes/orc-1/document`).flush(new Blob(['Documento indisponível']), {
      status: 404,
      statusText: 'Não encontrado',
    });

    expect(status).toBe(404);
  });
});
