import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { API_URL } from '@/app/environment/env';
import { QuotesService } from './quotes.service';

describe('QuotesService, erros de autorização', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  for (const status of [401, 403]) for (const detail of [false, true])
    it(`propaga ${status} sem substituir por demonstração (${detail ? 'detalhe' : 'lista'})`, () => {
      const service = TestBed.inject(QuotesService);
      const result: Observable<unknown> = detail ? service.getQuoteById('quote-1') : service.getQuotes();
      let receivedStatus = 0;
      result.subscribe({ next: () => fail('Resposta de demonstração indevida'), error: (error: HttpErrorResponse) => receivedStatus = error.status });
      TestBed.inject(HttpTestingController).expectOne(API_URL + '/api/quotes' + (detail ? '/quote-1' : ''))
        .flush({}, { status, statusText: 'Denied' });
      expect(receivedStatus).toBe(status);
    });
});
