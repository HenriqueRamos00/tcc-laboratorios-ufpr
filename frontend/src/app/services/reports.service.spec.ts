import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import type { Quote } from '@/app/model/quote';
import { QuotesService } from './quotes.service';
import { ReportsService } from './reports.service';

function quote(id: string): Quote {
  return {
    id, code: id, name: 'Demo', status: 'Finalizado', stage: 'Relatório publicado',
    description: '', companyName: 'Demo', externalContactName: '', externalContactEmail: '',
    totalPrice: 0, createdDate: null,
  };
}

describe('ReportsService, relatórios dos orçamentos autorizados', () => {
  let quotes: jasmine.SpyObj<QuotesService>;
  let service: ReportsService;

  beforeEach(() => {
    quotes = jasmine.createSpyObj<QuotesService>('QuotesService', ['getQuotes', 'getQuoteById']);
    TestBed.configureTestingModule({ providers: [{ provide: QuotesService, useValue: quotes }] });
    service = TestBed.inject(ReportsService);
  });

  it('divide os relatórios existentes entre os clientes sem reutilizar a lista anterior', () => {
    quotes.getQuotes.and.returnValue(of([quote('quote-18')]));
    service.publishedReports().subscribe((reports) => {
      expect(reports.length).toBe(4);
      expect(reports.every((report) => report.protocol === '00412/2026')).toBeTrue();
    });
    quotes.getQuotes.and.returnValue(of([quote('quote-15'), quote('quote-19')]));
    service.publishedReports().subscribe((reports) => {
      expect(reports.length).toBe(5);
      expect(reports.every((report) => report.protocol !== '00412/2026')).toBeTrue();
    });
  });

  it('uma conta sem orçamentos não recebe relatórios locais', () => {
    quotes.getQuotes.and.returnValue(of([]));
    service.publishedReports().subscribe((reports) => expect(reports).toEqual([]));
  });

  it('consulta o orçamento antes de entregar sua conclusão', () => {
    quotes.getQuoteById.and.returnValue(of(quote('quote-18')));
    service.analysisConclusion('quote-18').subscribe((conclusion) => {
      expect(conclusion?.quoteId).toBe('quote-18');
      expect(conclusion?.reports.length).toBe(4);
    });
    expect(quotes.getQuoteById).toHaveBeenCalledOnceWith('quote-18');
  });

  it('um orçamento autorizado sem conclusão mantém a resposta nula', () => {
    quotes.getQuoteById.and.returnValue(of(quote('quote-1')));
    service.analysisConclusion('quote-1').subscribe((conclusion) => expect(conclusion).toBeNull());
  });

  it('não entrega a conclusão de outro cliente quando a API responde 404', () => {
    const failure = new HttpErrorResponse({ status: 404 });
    quotes.getQuoteById.and.returnValue(throwError(() => failure));
    service.analysisConclusion('quote-18').subscribe({
      next: () => fail('Conclusão de outro cliente'),
      error: (error) => expect(error).toBe(failure),
    });
  });

  it('propaga falhas da API sem voltar à lista completa de relatórios', () => {
    const failure = new HttpErrorResponse({ status: 502 });
    quotes.getQuotes.and.returnValue(throwError(() => failure));
    service.publishedReports().subscribe({
      next: () => fail('Fallback de relatórios indevido'),
      error: (error) => expect(error).toBe(failure),
    });
  });
});
