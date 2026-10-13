import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import type { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';
import { QuotesService } from '@/app/services/quotes.service';

import { QuoteDetailComponent } from './quote-detail.component';

const ACEITAR = 'Aceitar Orçamento';
const RECUSAR = 'Recusar Orçamento';
const CONFIRMAR_ENVIO = 'Confirmar envio da amostra';
const LEMBRETE_DA_ETIQUETA = 'Identifique a caixa';

function orcamentoNaEtapa(nomeDaEtapa: string): Quote {
  return {
    id: 'quote-1',
    code: 'EAQ_2026_45487_V_1',
    name: 'Ensaio em transformador de força',
    status: 'Em Andamento',
    stage: nomeDaEtapa,
    description: 'Análise físico-química e cromatografia',
    companyName: 'Empresa Exemplo Alfa Ltda',
    externalContactName: 'Fulano de Tal',
    externalContactEmail: 'fulano@exemplo.com',
    totalPrice: 4200,
    createdDate: '2026-05-05',
  };
}

function montarDetalhe(nomeDaEtapa: string) {
  const destinos: unknown[][] = [];

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: QuotesService, useValue: { getQuoteById: () => of(orcamentoNaEtapa(nomeDaEtapa)) } },
      { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
      { provide: MatSnackBar, useValue: { open: () => {} } },
      {
        provide: AppDialogService,
        useValue: { open: () => ({ afterClosed: () => of(undefined) }) },
      },
    ],
  });

  spyOn(TestBed.inject(Router), 'navigate').and.callFake((comandos: unknown[]) => {
    destinos.push(comandos);
    return Promise.resolve(true);
  });

  const fixture = TestBed.createComponent(QuoteDetailComponent);
  fixture.componentRef.setInput('id', 'quote-1');
  fixture.detectChanges();
  fixture.detectChanges();

  return { fixture, tela: fixture.nativeElement as HTMLElement, destinos };
}

function botaoCom(tela: HTMLElement, rotulo: string): HTMLButtonElement | undefined {
  return Array.from(tela.querySelectorAll('button')).find((botao) =>
    botao.textContent?.includes(rotulo),
  );
}

describe('QuoteDetailComponent, a ação que a etapa pede', () => {
  it('em negociação a tela ainda pede a decisão do cliente', () => {
    const { tela } = montarDetalhe('Negociação');

    expect(botaoCom(tela, ACEITAR)).toBeDefined();
    expect(botaoCom(tela, RECUSAR)).toBeDefined();
    expect(botaoCom(tela, CONFIRMAR_ENVIO)).toBeUndefined();
  });

  // Aprovado é aprovado: oferecer aprovar de novo, ou recusar o que já foi
  // aceito, desfaz uma decisão que o cliente já tomou.
  it('aprovado pelo cliente troca a decisão pela confirmação do envio', () => {
    const { tela } = montarDetalhe('Aprovado pelo cliente');

    expect(botaoCom(tela, ACEITAR)).toBeUndefined();
    expect(botaoCom(tela, RECUSAR)).toBeUndefined();
    expect(botaoCom(tela, CONFIRMAR_ENVIO)).toBeDefined();
    expect(tela.textContent).toContain(LEMBRETE_DA_ETIQUETA);
  });

  it('com a amostra já a caminho não sobra ação nenhuma na tela', () => {
    const { tela } = montarDetalhe('Aguardando entrega da amostra');

    expect(botaoCom(tela, ACEITAR)).toBeUndefined();
    expect(botaoCom(tela, CONFIRMAR_ENVIO)).toBeUndefined();
    expect(tela.textContent).toContain('Acompanhe a situação pela listagem');
  });

  // A etapa que veio na carga da tela é a do Salesforce, que ainda não soube do
  // envio confirmado agora: quem manda é a decisão desta sessão.
  it('confirmado o envio, a tela deixa de pedir a confirmação outra vez', fakeAsync(() => {
    const { fixture, tela, destinos } = montarDetalhe('Aprovado pelo cliente');

    botaoCom(tela, CONFIRMAR_ENVIO)?.click();
    tick(500);
    fixture.detectChanges();

    expect(TestBed.inject(QuoteDecisionService).decisaoDe('quote-1')?.situacao).toBe(
      'aguardando-amostra',
    );
    expect(destinos).toEqual([['/cliente/orcamentos']]);
    expect(botaoCom(tela, CONFIRMAR_ENVIO)).toBeUndefined();
  }));
});
