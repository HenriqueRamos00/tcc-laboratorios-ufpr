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
  quoteCode: 'EAQ_2026_45496_V_1',
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

  it('filtra pelo código do orçamento quando ele é informado', () => {
    service.list({ quoteCode: 'EAQ_2026_45496_V_1', page: 1, pageSize: 50 }).subscribe();

    const request = http.expectOne((req) => req.url === `${API_URL}/api/reports`);
    expect(request.request.params.get('quoteCode')).toBe('EAQ_2026_45496_V_1');
    request.flush([]);
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
