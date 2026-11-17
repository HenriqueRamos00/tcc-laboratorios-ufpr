import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { PageRequest } from '@/app/model/page';
import type { Quote } from '@/app/model/quote';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';
import { QuotesService } from '@/app/services/quotes.service';
import { TourService } from '@/app/services/tour.service';
import { ate, balao, botao, mostrando, tituloDoBalao } from '@/app/services/tour.spec-util';

import { QuoteListComponent } from './quote-list.component';
import { quoteListTour } from './quote-list.tour';

function orcamento(index: number, etapa: string): Quote {
  return {
    id: `quote-${index}`,
    code: `EAQ_2026_${45000 + index}_V_1`,
    name: `Proposta ${index}`,
    status: 'Em Andamento',
    stage: etapa,
    description: `Proposta ${index}`,
    companyName: 'Empresa Exemplo Alfa Ltda',
    protocol: `L-${45000 + index}-2026-X`,
    externalContactName: 'Fulano de Tal',
    externalContactEmail: 'fulano@exemplo.com',
    totalPrice: 1000 + index,
    createdDate: '2026-05-05',
  };
}

function montarListagem(quotes: ReadonlyArray<Quote>): ComponentFixture<QuoteListComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: QuotesService,
        useValue: {
          list: (request: PageRequest) =>
            of({ items: quotes, total: quotes.length, page: request.page, pageSize: request.pageSize }),
        },
      },
      { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
      { provide: QuoteDecisionService, useValue: { decisions: () => new Map() } },
    ],
  });

  const fixture = TestBed.createComponent(QuoteListComponent);
  fixture.autoDetectChanges();
  return fixture;
}

function janelaDeStatus(): Element | null {
  return document.querySelector('app-status-proposta-dialog');
}

async function avancarAte(titulo: string): Promise<void> {
  for (let passos = 0; passos < 12 && tituloDoBalao() !== titulo; passos++) {
    const anterior = tituloDoBalao();
    botao('driver-popover-next-btn').click();
    await ate(() => tituloDoBalao() !== anterior, `passo seguinte a "${anterior}"`);
  }
  expect(tituloDoBalao()).toBe(titulo);
}

describe('QuoteListComponent, ajuda guiada', () => {
  let tour: TourService;

  afterEach(async () => {
    tour.stop();
    await ate(() => balao() === null && janelaDeStatus() === null, 'tour e janela encerrados');
  });

  it('todo passo da página tem o seu alvo na tela', () => {
    const fixture = montarListagem([orcamento(1, 'Negociação'), orcamento(2, 'Qualificação')]);
    tour = TestBed.inject(TourService);

    const naPagina = quoteListTour({ enter: () => undefined, leave: () => undefined }).steps.filter(
      (step) => !step.scene,
    );

    for (const step of naPagina) {
      expect(fixture.nativeElement.querySelector(`[data-tour="${step.target}"]`))
        .withContext(step.target)
        .not.toBeNull();
    }
  });

  it('abre a janela de status para explicá-la e a fecha ao seguir adiante', async () => {
    montarListagem([orcamento(1, 'Negociação'), orcamento(2, 'Qualificação')]);
    tour = TestBed.inject(TourService);
    expect(tour.hasPageTour()).toBeTrue();

    await tour.startPage();
    await mostrando('Filtrar por situação');
    await avancarAte('Verificar Status');
    expect(janelaDeStatus()).toBeNull();

    await avancarAte('Linha de etapas');
    expect(janelaDeStatus()).not.toBeNull();
    expect(janelaDeStatus()!.querySelector('[data-tour="status-etapas"]')!.classList).toContain(
      'driver-active-element',
    );

    await avancarAte('O que acontece agora');
    await avancarAte('Baixar e falar com o laboratório');
    await avancarAte('Visualizar Orçamento');
    await ate(() => janelaDeStatus() === null, 'janela de status fechada ao sair da cena');
  });

  it('encerrar o tour com a janela aberta também a fecha', async () => {
    montarListagem([orcamento(1, 'Negociação')]);
    tour = TestBed.inject(TourService);

    await tour.startPage();
    await mostrando('Filtrar por situação');
    await avancarAte('Linha de etapas');
    expect(janelaDeStatus()).not.toBeNull();

    tour.stop();

    await ate(() => janelaDeStatus() === null, 'janela de status fechada ao encerrar');
  });

  it('com a lista vazia pula os passos dos cartões e da janela, sem travar', async () => {
    montarListagem([]);
    tour = TestBed.inject(TourService);

    await tour.startPage();
    await mostrando('Filtrar por situação');
    await avancarAte('Pesquisar');

    expect(botao('driver-popover-next-btn').textContent).toBe('Concluir');
    botao('driver-popover-next-btn').click();
    await ate(() => !tour.active(), 'tour encerrado ao concluir');
    expect(janelaDeStatus()).toBeNull();
  });
});
