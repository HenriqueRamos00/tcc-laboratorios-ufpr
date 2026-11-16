import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { API_URL } from '@/app/environment/env';
import type { Page } from '@/app/model/page';
import type { Quote } from '@/app/model/quote';
import { QuotesService } from './quotes.service';

describe('QuotesService, erros da API', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  for (const status of [400, 401, 403, 404, 502]) for (const detail of [false, true])
    it(`propaga ${status} sem substituir por demonstração (${detail ? 'detalhe' : 'lista'})`, () => {
      const service = TestBed.inject(QuotesService);
      const result: Observable<unknown> = detail
        ? service.getQuoteById('quote-1')
        : service.list({ page: 1, pageSize: 50 });
      let receivedStatus = 0;
      result.subscribe({ next: () => fail('Resposta de demonstração indevida'), error: (error: HttpErrorResponse) => receivedStatus = error.status });
      TestBed.inject(HttpTestingController)
        .expectOne((request) => request.url === API_URL + '/api/quotes' + (detail ? '/quote-1' : ''))
        .flush({}, { status, statusText: 'Denied' });
      expect(receivedStatus).toBe(status);
    });
});

describe('QuotesService, paginação', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('pede a página e lê o total dos cabeçalhos, como as demais listas', () => {
    let page: Page<Quote> | undefined;
    TestBed.inject(QuotesService).list({ page: 3, pageSize: 20 }).subscribe((result) => (page = result));

    const request = TestBed.inject(HttpTestingController).expectOne(
      (candidate) => candidate.url === API_URL + '/api/quotes',
    );
    expect(request.request.params.get('page')).toBe('3');
    expect(request.request.params.get('pageSize')).toBe('20');
    request.flush([{ id: 'quote-41' }], {
      headers: { 'X-Total-Count': '73', 'X-Page': '3', 'X-Page-Size': '20' },
    });

    expect(page?.total).toBe(73);
    expect(page?.page).toBe(3);
    expect(page?.pageSize).toBe(20);
    expect(page?.items.map((quote) => quote.id)).toEqual(['quote-41']);
  });
});
