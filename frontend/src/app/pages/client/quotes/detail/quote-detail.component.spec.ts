import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import type { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';
import { QuotesService } from '@/app/services/quotes.service';

import { usePdfViewerStub } from './pdf-viewer.spec-util';
import { QuoteDetailComponent } from './quote-detail.component';

const ACCEPT_BUTTON = 'Aceitar Orçamento';
const REJECT_BUTTON = 'Recusar Orçamento';
const CONFIRM_SHIPMENT_BUTTON = 'Confirmar envio da amostra';
const LABEL_REMINDER = 'Identifique a caixa';

function quoteAtStage(stageName: string): Quote {
  return {
    id: 'quote-1',
    code: 'EAQ_2026_45487_V_1',
    name: 'Ensaio em transformador de força',
    status: 'Em Andamento',
    stage: stageName,
    description: 'Análise físico-química e cromatografia',
    companyName: 'Empresa Exemplo Alfa Ltda',
    externalContactName: 'Fulano de Tal',
    externalContactEmail: 'fulano@exemplo.com',
    totalPrice: 4200,
    createdDate: '2026-05-05',
  };
}

function mountDetail(stageName: string) {
  const destinations: unknown[][] = [];

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: QuotesService,
        useValue: {
          getQuoteById: () => of(quoteAtStage(stageName)),
          quoteDocument: () => of(new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
        },
      },
      { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
      { provide: MatSnackBar, useValue: { open: () => {} } },
      {
        provide: AppDialogService,
        useValue: { open: () => ({ afterClosed: () => of(undefined) }) },
      },
    ],
  });

  usePdfViewerStub();

  spyOn(TestBed.inject(Router), 'navigate').and.callFake((commands: unknown[]) => {
    destinations.push(commands);
    return Promise.resolve(true);
  });

  const fixture = TestBed.createComponent(QuoteDetailComponent);
  fixture.componentRef.setInput('id', 'quote-1');
  fixture.detectChanges();
  fixture.detectChanges();

  return { fixture, view: fixture.nativeElement as HTMLElement, destinations };
}

function buttonWith(view: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(view.querySelectorAll('button')).find((button) =>
    button.textContent?.includes(label),
  );
}

describe('QuoteDetailComponent, a ação que a etapa pede', () => {
  it('em negociação a tela ainda pede a decisão do cliente', () => {
    const { view } = mountDetail('Negociação');

    expect(buttonWith(view, ACCEPT_BUTTON)).toBeDefined();
    expect(buttonWith(view, REJECT_BUTTON)).toBeDefined();
    expect(buttonWith(view, CONFIRM_SHIPMENT_BUTTON)).toBeUndefined();
  });

  // Aprovado é aprovado: oferecer aprovar de novo, ou recusar o que já foi
  // aceito, desfaz uma decisão que o cliente já tomou.
  it('aprovado pelo cliente troca a decisão pela confirmação do envio', () => {
    const { view } = mountDetail('Aprovado pelo cliente');

    expect(buttonWith(view, ACCEPT_BUTTON)).toBeUndefined();
    expect(buttonWith(view, REJECT_BUTTON)).toBeUndefined();
    expect(buttonWith(view, CONFIRM_SHIPMENT_BUTTON)).toBeDefined();
    expect(view.textContent).toContain(LABEL_REMINDER);
  });

  // Limite de baixo: nas etapas sem proposta escrita não há o que aprovar, e
  // oferecer "Aceitar" ali contradiz a listagem, que nem mostra o orçamento.
  it('proposta ainda não escrita não oferece decisão nenhuma', () => {
    const { view } = mountDetail('Elaborando proposta');

    expect(buttonWith(view, ACCEPT_BUTTON)).toBeUndefined();
    expect(buttonWith(view, REJECT_BUTTON)).toBeUndefined();
    expect(buttonWith(view, CONFIRM_SHIPMENT_BUTTON)).toBeUndefined();
    expect(view.textContent).toContain('Proposta em elaboração');
    expect(view.textContent).toContain('Enquanto a proposta é preparada');
  });

  it('com a amostra já a caminho não sobra ação nenhuma na tela', () => {
    const { view } = mountDetail('Aguardando entrega da amostra');

    expect(buttonWith(view, ACCEPT_BUTTON)).toBeUndefined();
    expect(buttonWith(view, CONFIRM_SHIPMENT_BUTTON)).toBeUndefined();
    expect(view.textContent).toContain('Acompanhe a situação pela listagem');
  });

  // A etapa que veio na carga da tela é a do Salesforce, que ainda não soube do
  // envio confirmado agora: quem manda é a decisão desta sessão.
  it('confirmado o envio, a tela deixa de pedir a confirmação outra vez', fakeAsync(() => {
    const { fixture, view, destinations } = mountDetail('Aprovado pelo cliente');

    buttonWith(view, CONFIRM_SHIPMENT_BUTTON)?.click();
    tick(500);
    fixture.detectChanges();

    expect(TestBed.inject(QuoteDecisionService).decisionFor('quote-1')?.status).toBe(
      'aguardando-amostra',
    );
    expect(destinations).toEqual([['/cliente/quotes']]);
    expect(buttonWith(view, CONFIRM_SHIPMENT_BUTTON)).toBeUndefined();
  }));
});
