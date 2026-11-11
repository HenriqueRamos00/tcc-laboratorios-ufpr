import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import type { AnalysisConclusion, PublishedReport } from '@/app/model/report';
import { ReportsService } from './reports.service';

const RELATORIOS: readonly PublishedReport[] = [
  {
    id: '804',
    code: '804',
    protocol: 'L-45490-2026-X',
    sample: 'AM-2026-0142 - Óleo isolante',
    laboratory: 'Laboratório de Ensaios',
    publishedOn: '07/11/2026',
  },
];

const CONCLUSAO: AnalysisConclusion = {
  quoteId: '512',
  quoteCode: 'ORC-2026-0091',
  protocol: 'L-45490-2026-X',
  testType: 'Análise cromatográfica',
  summary: '1 relatório publicado disponível para consulta e download.',
  reports: RELATORIOS,
};

describe('ReportsService, consulta de relatórios publicados', () => {
  let service: ReportsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReportsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta a lista geral de relatórios publicados', () => {
    let result: readonly PublishedReport[] | undefined;
    service.publishedReports().subscribe((reports) => (result = reports));

    const request = http.expectOne(`${API_URL}/api/reports`);
    expect(request.request.method).toBe('GET');
    request.flush(RELATORIOS);

    expect(result).toEqual(RELATORIOS);
  });

  it('consulta os relatórios do orçamento', () => {
    let result: AnalysisConclusion | null | undefined;
    service.analysisConclusion('512').subscribe((conclusion) => (result = conclusion));

    const request = http.expectOne(`${API_URL}/api/quotes/512/reports`);
    expect(request.request.method).toBe('GET');
    request.flush(CONCLUSAO);

    expect(result).toEqual(CONCLUSAO);
  });

  it('preserva resposta sem relatório publicado para o orçamento', () => {
    let result: AnalysisConclusion | null | undefined;
    service.analysisConclusion('512').subscribe((conclusion) => (result = conclusion));

    http.expectOne(`${API_URL}/api/quotes/512/reports`).flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    expect(result).toBeNull();
  });

  it('solicita os bytes do PDF pelo identificador próprio do relatório', () => {
    const pdf = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    let result: Blob | undefined;
    service.downloadReport('804').subscribe((file) => (result = file));

    const request = http.expectOne(`${API_URL}/api/reports/804/file`);
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(pdf);

    expect(result).toBe(pdf);
  });

  it('propaga falhas da fonte sem voltar a dados de demonstração', () => {
    let received: unknown;
    service.publishedReports().subscribe({ error: (error) => (received = error) });

    http.expectOne(`${API_URL}/api/reports`).flush(null, { status: 502, statusText: 'Bad Gateway' });

    expect(received).toBeInstanceOf(HttpErrorResponse);
    expect((received as HttpErrorResponse).status).toBe(502);
  });

  it('propaga a ausência de PDF publicado sem criar um arquivo substituto', () => {
    let received: unknown;
    service.downloadReport('inexistente').subscribe({ error: (error) => (received = error) });

    http.expectOne(`${API_URL}/api/reports/inexistente/file`).flush(null, {
      status: 404,
      statusText: 'Not Found',
    });

    expect(received).toEqual(jasmine.any(HttpErrorResponse));
    expect((received as HttpErrorResponse).status).toBe(404);
  });
});
