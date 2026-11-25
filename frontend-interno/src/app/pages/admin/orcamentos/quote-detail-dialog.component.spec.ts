import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import pt from '@angular/common/locales/pt';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';

import { OrcamentoDetalhe } from '@/app/model/orcamento';
import { OrcamentosService } from '@/app/services/orcamentos.service';
import { QuoteDetailDialogComponent } from './quote-detail-dialog.component';

registerLocaleData(pt);

describe('Detalhes do orçamento', () => {
  let service: jasmine.SpyObj<OrcamentosService>;

  const detalhe: OrcamentoDetalhe = {
    id: 'quote-1',
    code: 'EAQ_2026_45482_V_1',
    name: 'Análise de óleo isolante',
    status: 'PendingAcceptance',
    stage: 'PendingAcceptance',
    description: 'Análise físico-química de óleo isolante',
    companyName: 'Copel Distribuição',
    externalContactName: 'Ana Lima',
    externalContactEmail: 'ana@example.com',
    createdDate: '2026-10-15T10:00:00Z',
    hasDocument: true,
    companyId: 'company-1',
    laboratoryName: 'Laboratório de Ensaios',
    protocol: 'L-45482-2026-X',
    scope: 'Análise conforme plano de amostragem',
    totalPrice: 37.5,
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533', unitPrice: 12.5 }],
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<OrcamentosService>(
      'OrcamentosService',
      ['list', 'get', 'downloadDocument', 'downloadPurchaseOrder'],
    );
    TestBed.configureTestingModule({
      imports: [QuoteDetailDialogComponent],
      providers: [
        { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { timezone: '-0300' } },
        { provide: OrcamentosService, useValue: service },
        { provide: MAT_DIALOG_DATA, useValue: { quoteId: 'quote-1', quoteCode: detalhe.code } },
      ],
    });
  });

  it('consulta e exibe os dados persistidos para leitura', fakeAsync(() => {
    service.get.and.returnValue(of(detalhe));
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(service.get).toHaveBeenCalledOnceWith('quote-1');
    expect(fixture.nativeElement.textContent).toContain('Copel Distribuição');
    expect(fixture.nativeElement.textContent).toContain('Laboratório de Ensaios');
    expect(fixture.nativeElement.textContent).toContain('Análise conforme plano de amostragem');
    expect(fixture.nativeElement.textContent).toContain('ASTM D1533');
    expect(fixture.nativeElement.textContent).toContain('Disponível para baixar na listagem');
    const camposValor = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('dt'))
      .filter((rotulo) => rotulo.textContent?.trim() === 'Valor');
    expect(camposValor.length).toBe(1);
    expect(camposValor[0].parentElement?.querySelector('dd')?.textContent?.replace(/\s/g, ''))
      .toContain('R$37,50');

    const subtotais = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('dt'))
      .filter((rotulo) => rotulo.textContent?.trim() === 'Subtotal')
      .map((rotulo) => rotulo.parentElement?.querySelector('dd')?.textContent?.replace(/\s/g, ''));
    expect(subtotais).toEqual(['R$37,50']);
    fixture.destroy();
  }));

  it('indica campos ausentes nos orçamentos legados sem criar itens', fakeAsync(() => {
    service.get.and.returnValue(of({
      ...detalhe,
      laboratoryName: null,
      protocol: 'L-45483-2026-X',
      scope: null,
      items: [],
    }));
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Laboratório');
    expect(fixture.nativeElement.textContent).toContain('Escopo');
    expect(fixture.nativeElement.textContent).toContain('Itens não informados para este orçamento.');
    expect(fixture.nativeElement.textContent).toContain('Não informado');
    fixture.destroy();
  }));

  it('exibe a justificativa da recusa no detalhe administrativo', fakeAsync(() => {
    service.get.and.returnValue(of({
      ...detalhe,
      status: 'Refused',
      stage: 'Fechado recusado Lactec',
      clientDecision: {
        acceptance: null,
        rejection: {
          refusedAt: '2026-11-21T09:15:00Z',
          justification: 'Prazo de entrega incompatível com a necessidade da empresa.',
        },
      },
    }));
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Motivo da recusa');
    expect(fixture.nativeElement.textContent)
      .toContain('Prazo de entrega incompatível com a necessidade da empresa.');
    fixture.destroy();
  }));

  it('exibe os campos do termo de aceite preenchido pelo cliente', fakeAsync(() => {
    service.get.and.returnValue(of({
      ...detalhe,
      status: 'InProgress',
      clientDecision: {
        acceptance: {
          method: 'TERMO_DE_ACEITE',
          acceptedAt: '2026-11-20T14:30:00Z',
          hasPurchaseOrder: false,
          terms: {
            billing: {
              company: 'Copel Distribuição',
              cnpj: '00000001000136',
              stateRegistration: null,
              street: 'Rua de Cobrança, 123',
              city: 'Curitiba',
              state: 'PR',
              district: null,
              postalCode: '80000-000',
              contact: null,
              phone: null,
              fax: null,
              invoiceEmails: 'financeiro@example.test',
            },
            approval: {
              requesterName: 'Ana Solicitante',
              responsibleName: 'Carlos Responsável',
              jobTitle: 'Gerente',
              department: null,
              date: '2026-11-19',
              allowsResultsByEmail: true,
              resultsEmail: 'resultados@example.test',
            },
          },
        },
        rejection: null,
      },
    }));
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Aceite do cliente');
    expect(fixture.nativeElement.textContent).toContain('Termo de aceite');
    expect(fixture.nativeElement.textContent).toContain('Ver dados do termo preenchido');
    expect(fixture.nativeElement.textContent).toContain('Rua de Cobrança, 123');
    expect(fixture.nativeElement.textContent).toContain('Carlos Responsável');
    expect(fixture.nativeElement.textContent).toContain('resultados@example.test');
    fixture.destroy();
  }));

  it('abre o PDF da ordem de compra em outra aba', fakeAsync(() => {
    service.get.and.returnValue(of({
      ...detalhe,
      status: 'InProgress',
      clientDecision: {
        acceptance: {
          method: 'ORDEM_DE_COMPRA',
          acceptedAt: '2026-11-22T21:52:00Z',
          terms: null,
          hasPurchaseOrder: true,
        },
        rejection: null,
      },
    }));
    service.downloadPurchaseOrder.and.returnValue(of(new HttpResponse({
      body: new Blob(['%PDF-1.7 ordem de compra'], { type: 'application/pdf' }),
      headers: new HttpHeaders({
        'Content-Type': 'application/pdf',
        'Content-Disposition': 'inline; filename="ordem.pdf"',
      }),
    })));
    const janela = {
      opener: null,
      close: jasmine.createSpy('close'),
      location: { href: '' },
    } as unknown as Window;
    spyOn(window, 'open').and.returnValue(janela);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:ordem-de-compra');
    const revogarUrl = spyOn(URL, 'revokeObjectURL');

    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('O cliente enviou uma ordem de compra.');
    const botao = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((elemento) => elemento.textContent?.includes('Abrir ordem de compra'));
    expect(botao).toBeDefined();
    botao?.click();
    fixture.detectChanges();

    expect(service.downloadPurchaseOrder).toHaveBeenCalledOnceWith('quote-1');
    expect(janela.location.href).toBe('blob:ordem-de-compra');
    expect(janela.close).not.toHaveBeenCalled();
    tick(60_000);
    expect(revogarUrl).toHaveBeenCalledOnceWith('blob:ordem-de-compra');
    fixture.destroy();
  }));

  it('exibe no detalhe o mesmo dia local da tabela perto da meia-noite UTC', fakeAsync(() => {
    service.get.and.returnValue(of({ ...detalhe, createdDate: '2026-10-18T02:30:00Z' }));
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('17/10/2026');
    expect(fixture.nativeElement.textContent).not.toContain('18/10/2026');
    fixture.destroy();
  }));

  it('mantém o botão Fechar no rodapé fora da área rolável durante carregamento e erro', fakeAsync(() => {
    const respostaPendente = new Subject<OrcamentoDetalhe>();
    service.get.and.returnValue(respostaPendente);
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();

    const rodape = fixture.nativeElement.querySelector('.dialog-shell__footer');
    const conteudo = fixture.nativeElement.querySelector('.dialog-shell__content');
    expect(rodape.querySelector('button[dialog-footer]')).not.toBeNull();
    expect(conteudo.querySelector('button[dialog-footer]')).toBeNull();

    respostaPendente.error(new HttpErrorResponse({ status: 404, statusText: 'Not Found' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dialog-shell__footer button[dialog-footer]')).not.toBeNull();
    fixture.destroy();
  }));

  it('mostra erro do detalhe sem afetar a listagem', fakeAsync(() => {
    service.get.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    const fixture = TestBed.createComponent(QuoteDetailDialogComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.componentInstance.erro()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Este orçamento não está mais disponível.');
    expect(service.list).not.toHaveBeenCalled();
    fixture.destroy();
  }));
});
