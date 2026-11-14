import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import type { Report } from '@/app/model/report';

import { ReportsService } from './reports.service';

const REPORT: Report = {
  id: 'b2lkLTE',
  identification: 'LACTEC-00307/2026',
  requestNumber: 'S-00307/2026',
  quoteCode: '2026_45496',
  sampleId: 'AM-1',
  sampleName: 'Transformador TR-01',
  client: 'EMPRESA DE ENERGIA',
  status: 'Publicado',
  scheduledDate: '2026-03-10',
  issueDate: '2026-03-07',
  publicationDate: '2026-03-08',
  hasSignedDocument: true,
};

describe('ReportsService', () => {
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

  it('lista a página pedida e lê o total do cabeçalho', () => {
    let total = -1;
    let items: readonly Report[] = [];
    service.list({ page: 2, pageSize: 50 }).subscribe((page) => {
      total = page.total;
      items = page.items;
    });

    http
      .expectOne(`${API_URL}/api/reports?page=2&pageSize=50`)
      .flush([REPORT], { headers: { 'X-Total-Count': '51', 'X-Page': '2', 'X-Page-Size': '50' } });

    expect(total).toBe(51);
    expect(items).toEqual([REPORT]);
  });

  it('filtra pelo OrcamentoCRM do AutoLAB quando ele é informado', () => {
    service.list({ quoteCode: '2026_45496', page: 1, pageSize: 50 }).subscribe();

    const request = http.expectOne((req) => req.url === `${API_URL}/api/reports`);
    expect(request.request.params.get('quoteCode')).toBe('2026_45496');
    request.flush([]);
  });

  it('consulta os laudos pelo ID autorizado do orçamento e preserva a conclusão do Spring', () => {
    let result: import('./reports.service').ReportPage | undefined;
    service.list({ quoteId: '52', quoteCode: 'ORC-52', page: 2, pageSize: 1 })
      .subscribe((page) => (result = page));
    const request = http.expectOne(`${API_URL}/api/quotes/52/reports?page=2&pageSize=1`);
    request.flush({ reports: [REPORT], testType: 'Cromatografia', summary: 'Resultado publicado' },
      { headers: { 'X-Total-Count': '3' } });
    expect(result?.items).toEqual([REPORT]);
    expect(result?.total).toBe(3);
    expect(result?.summary).toBe('Resultado publicado');
    expect(result?.testType).toBe('Cromatografia');
  });

  it('204 do orçamento representa ausência de laudos, sem consultar a listagem geral', () => {
    let count = -1;
    service.list({ quoteId: '52', page: 1, pageSize: 50 }).subscribe((page) => (count = page.items.length));
    http.expectOne(`${API_URL}/api/quotes/52/reports?page=1&pageSize=50`)
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(count).toBe(0);
  });

  it('baixa o PDF assinado com o nome do Content-Disposition', () => {
    let name: string | null = null;
    service.signedDocument('b2lkLTE').subscribe((file) => (name = file.name));

    http
      .expectOne(`${API_URL}/api/reports/b2lkLTE/document`)
      .flush(new Blob(['%PDF-1.4']), {
        headers: { 'Content-Disposition': "attachment; filename*=UTF-8''LACTEC-00307%2F2026.pdf" },
      });

    expect(name as string | null).toBe('LACTEC-00307/2026.pdf');
  });

  it('sem Content-Disposition, devolve o arquivo sem nome para a tela escolher um', () => {
    let name: string | null = 'x';
    service.signedDocument('b2lkLTE').subscribe((file) => (name = file.name));

    http.expectOne(`${API_URL}/api/reports/b2lkLTE/document`).flush(new Blob(['%PDF-1.4']));

    expect(name as string | null).toBeNull();
  });
});
