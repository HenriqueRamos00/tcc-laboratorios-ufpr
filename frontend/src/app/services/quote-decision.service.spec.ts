import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import type { AcceptanceTerms } from '@/app/model/acceptance';

import { AcceptanceDraftService } from './acceptance-draft.service';
import { QuoteDecisionService, type RecordedDecision } from './quote-decision.service';

describe('QuoteDecisionService', () => {
  let service: QuoteDecisionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(QuoteDecisionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const respond = (kind: string) => ({ id: 'decisao-1', kind, recordedAt: '2026-11-15T12:00:00Z' });

  it('envia o termo de aceite ao portal e só registra a decisão depois da resposta', () => {
    const terms: AcceptanceTerms = {
      proposalId: 'quote 3',
      billing: {
        company: 'EMPRESA', cnpj: '30960572000183', stateRegistration: '', street: 'Rua 1', city: 'Curitiba',
        state: 'PR', district: '', postalCode: '80000-000', contact: '', phone: '', invoiceEmails: ['financeiro@empresa.com', 'fiscal@empresa.com'],
      },
      approval: {
        requesterName: 'Marina', responsibleName: 'Joao', jobTitle: 'Engenheira', department: '',
        date: '2026-11-15', allowsResultsByEmail: false, resultsEmail: '',
      },
    };
    let recorded: RecordedDecision | undefined;

    service.aceitar(terms).subscribe((value) => (recorded = value));
    const request = http.expectOne(`${API_URL}/api/quotes/quote%203/acceptance`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ billing: terms.billing, approval: terms.approval });
    expect(service.decisionFor('quote 3')).toBeUndefined();

    request.flush(respond('acceptance'));

    expect(recorded?.status).toBe('aprovado-pelo-cliente');
    expect(service.decisionFor('quote 3')?.recordedAt.toISOString()).toBe('2026-11-15T12:00:00.000Z');
  });

  it('manda a ordem de compra como arquivo no campo file', () => {
    const pdf = new File(['%PDF-1.7'], 'oc.pdf', { type: 'application/pdf' });

    service.aceitarComOrdemDeCompra('quote-3', pdf).subscribe();
    const request = http.expectOne(`${API_URL}/api/quotes/quote-3/purchase-order`);
    const form = request.request.body as FormData;
    expect((form.get('file') as File).name).toBe('oc.pdf');
    request.flush(respond('purchase-order'));

    expect(service.decisionFor('quote-3')?.status).toBe('aprovado-pelo-cliente');
  });

  it('confirma o envio da amostra e registra a recusa com a justificativa', () => {
    service.confirmSampleShipment('quote-6').subscribe();
    http.expectOne(`${API_URL}/api/quotes/quote-6/sample-shipment`).flush(respond('sample-shipment'));
    service.recusar({ proposalId: 'quote-1', justification: 'fora do orçamento' }).subscribe();
    const rejection = http.expectOne(`${API_URL}/api/quotes/quote-1/rejection`);
    expect(rejection.request.body).toEqual({ justification: 'fora do orçamento' });
    rejection.flush(respond('rejection'));

    expect(service.decisionFor('quote-6')?.status).toBe('aguardando-amostra');
    expect(service.decisionFor('quote-1')?.status).toBe('recusado');
  });

  it('apaga o rascunho do termo quando o orçamento recebe uma decisão, e só então', () => {
    const drafts = TestBed.inject(AcceptanceDraftService);
    const discard = spyOn(drafts, 'discard');

    service.recusar({ proposalId: 'quote-1', justification: 'fora do orçamento' }).subscribe();
    const rejection = http.expectOne(`${API_URL}/api/quotes/quote-1/rejection`);
    expect(discard).not.toHaveBeenCalled();
    rejection.flush(respond('rejection'));

    service.confirmSampleShipment('quote-3').subscribe({ error: () => {} });
    http
      .expectOne(`${API_URL}/api/quotes/quote-3/sample-shipment`)
      .flush({ code: 'client-decision-not-allowed' }, { status: 409, statusText: 'Conflict' });

    expect(discard.calls.allArgs()).toEqual([['quote-1']]);
  });

  it('não registra nada quando o portal recusa a decisão', () => {
    let failed = false;

    service.confirmSampleShipment('quote-3').subscribe({ error: () => (failed = true) });
    http
      .expectOne(`${API_URL}/api/quotes/quote-3/sample-shipment`)
      .flush({ code: 'client-decision-not-allowed' }, { status: 409, statusText: 'Conflict' });

    expect(failed).toBeTrue();
    expect(service.decisionFor('quote-3')).toBeUndefined();
  });
});
