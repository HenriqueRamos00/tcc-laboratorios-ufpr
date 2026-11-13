import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RelatoriosService } from './relatorios.service';

describe('Serviço de relatórios', () => {
  let service: RelatoriosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RelatoriosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta a página de relatórios e omite filtros sem seleção', () => {
    service.list({
      search: '  óleo  ',
      validationStatus: null,
      published: null,
      page: 2,
      pageSize: 20,
    }).subscribe();

    const request = http.expectOne((item) => item.url === '/api/reports');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('search')).toBe('óleo');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('pageSize')).toBe('20');
    expect(request.request.params.has('validationStatus')).toBeFalse();
    expect(request.request.params.has('published')).toBeFalse();
    request.flush({ items: [], page: 2, pageSize: 20, totalItems: 0 });
  });

  it('envia status documental e publicação selecionados na pesquisa', () => {
    service.list({
      search: '',
      validationStatus: 'pending-validation',
      published: false,
      page: 1,
      pageSize: 20,
    }).subscribe();

    const request = http.expectOne((item) => item.url === '/api/reports');
    expect(request.request.params.get('validationStatus')).toBe('pending-validation');
    expect(request.request.params.get('published')).toBe('false');
    request.flush({ items: [], page: 1, pageSize: 20, totalItems: 0 });
  });

  it('consulta o detalhe do relatório pelo identificador codificado', () => {
    service.get('registro 42').subscribe();

    const request = http.expectOne('/api/reports/registro%2042');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('valida o relatório enviando versão e fingerprint sem autoria do cliente', () => {
    const contentFingerprint = 'a'.repeat(64);
    service.validate('42', 7, contentFingerprint).subscribe();

    const request = http.expectOne('/api/reports/42/validation');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ version: 7, contentFingerprint });
    request.flush({
      id: '42',
      validationStatus: 'validated',
      published: false,
      validatedAt: '2026-11-07T15:04:12Z',
      validatedBy: { id: '12', name: 'Técnica', unit: 'Química' },
      publishedAt: null,
      version: 8,
      pdfAvailable: true,
    });
  });

  it('publica relatório validado enviando somente a versão atual', () => {
    service.publish('42', 8).subscribe();

    const request = http.expectOne('/api/reports/42/publication');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ version: 8 });
    request.flush({
      id: '42',
      validationStatus: 'validated',
      published: true,
      publishedAt: '2026-11-07T16:10:00Z',
      publishedBy: { id: '12', name: 'Técnica', unit: 'Química' },
      version: 9,
      pdfAvailable: true,
    });
  });

  it('obtém o arquivo PDF como blob com os cabeçalhos do servidor', () => {
    service.pdf('42').subscribe((response) => {
      expect(response.headers.get('Content-Disposition')).toContain('relatorio-42.pdf');
      expect(response.body?.type).toBe('application/pdf');
    });

    const request = http.expectOne('/api/reports/42/pdf');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['PDF real'], { type: 'application/pdf' }), {
      headers: new HttpHeaders({
        'Content-Type': 'application/pdf',
        'Content-Disposition': 'inline; filename="relatorio-42.pdf"',
      }),
    });
  });
});
