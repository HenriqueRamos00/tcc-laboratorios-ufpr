import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import pt from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
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
    scope: 'Análise conforme plano de amostragem',
    totalPrice: 37.5,
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533', unitPrice: 12.5 }],
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<OrcamentosService>('OrcamentosService', ['list', 'get', 'downloadDocument']);
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
