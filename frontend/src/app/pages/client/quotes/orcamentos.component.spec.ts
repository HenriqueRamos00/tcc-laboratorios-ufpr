import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { API_URL } from '@/app/environment/env';
import { Quote } from '@/app/model/quote';
import { LoginService } from '@/app/services/login.service';
import { ClientOrcamentosComponent } from './orcamentos.component';

const orcamento: Quote = {
  id: '1', code: 'EAQ-1', name: 'Análise de óleo', description: 'Óleo isolante',
  companyName: 'Empresa vinculada', externalContactName: 'Ana', externalContactEmail: 'ana@example.com',
  stage: 'Em negociação', status: 'Em negociação', totalPrice: 1200, createdDate: null,
};

describe('Lista de orçamentos do cliente', () => {
  let fixture: ComponentFixture<ClientOrcamentosComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ClientOrcamentosComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations(),
        { provide: LoginService, useValue: { currentUser: () => 'Ana Lima' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ClientOrcamentosComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('mostra carregamento antes de informar que a lista está vazia', () => {
    expect(fixture.nativeElement.textContent).toContain('Carregando orçamentos');
    expect(fixture.nativeElement.textContent).not.toContain('Nenhum orçamento encontrado');

    http.expectOne(`${API_URL}/client/quotes`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum orçamento encontrado');
    expect(fixture.nativeElement.textContent).not.toContain('Carregando orçamentos');
  });

  it('permite tentar novamente após falha ao carregar os orçamentos', () => {
    http.expectOne(`${API_URL}/client/quotes`).flush({}, {
      status: 503, statusText: 'Serviço indisponível',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os orçamentos');

    const tela: HTMLElement = fixture.nativeElement;
    const botoes = Array.from(tela.querySelectorAll('button'));
    botoes.find((botao) => botao.textContent?.includes('Tentar novamente'))!.click();
    http.expectOne(`${API_URL}/client/quotes`).flush([orcamento]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('EAQ-1');
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar os orçamentos');
  });

  it('separa pendente de aceite e finalizado sem adivinhar status desconhecido', () => {
    http.expectOne(`${API_URL}/client/quotes`).flush([
      { ...orcamento, code: 'PENDENTE', stage: '', status: 'Pendente de Aceite' },
      { ...orcamento, id: '2', code: 'FINALIZADO', stage: 'Finalizado', status: 'Finalizado' },
      { ...orcamento, id: '3', code: 'DESCONHECIDO', stage: 'Aceite em revisão', status: 'Aceite em revisão' },
      { ...orcamento, id: '4', code: 'EM-ANDAMENTO', stage: '', status: 'Em Andamento' },
    ]);
    const component = fixture.componentInstance;

    component.setFilter('pendentes');
    expect(component.visibleOrcamentos().map((item) => item.code)).toEqual(['PENDENTE']);
    component.setFilter('finalizados');
    expect(component.visibleOrcamentos().map((item) => item.code)).toEqual(['FINALIZADO']);
    component.setFilter('andamento');
    expect(component.visibleOrcamentos().map((item) => item.code)).toEqual(['EM-ANDAMENTO']);
    component.setFilter('todos');
    expect(component.visibleOrcamentos().length).toBe(4);
  });
});
