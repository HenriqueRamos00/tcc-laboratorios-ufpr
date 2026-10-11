import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Quote } from '@/app/model/quote';
import { LoginService } from '@/app/services/login.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { QuoteListComponent } from './quote-list.component';

const orcamento: Quote = {
  id: '1', code: 'EAQ-1', name: 'Análise de óleo', description: 'Óleo isolante',
  companyName: 'Empresa vinculada', externalContactName: 'Ana', externalContactEmail: 'ana@example.com',
  stage: 'Em negociação', status: 'Em negociação', totalPrice: 1200, createdDate: null,
};

describe('Lista de orçamentos do cliente', () => {
  let fixture: ComponentFixture<QuoteListComponent>;
  let http: HttpTestingController;
  const openDialog = jasmine.createSpy('open');

  beforeEach(() => {
    openDialog.calls.reset();
    openDialog.and.returnValue({ afterClosed: () => of(undefined) });
    TestBed.configureTestingModule({
      imports: [QuoteListComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations(),
        { provide: LoginService, useValue: { currentUser: () => 'Ana Lima' } },
        { provide: AppDialogService, useValue: { open: openDialog } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QuoteListComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('mostra carregamento antes de informar que a lista está vazia', () => {
    expect(fixture.nativeElement.textContent).toContain('Carregando orçamentos');
    expect(fixture.nativeElement.textContent).not.toContain('Nenhum orçamento encontrado');

    http.expectOne(`${API_URL}/quotes`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum orçamento encontrado');
    expect(fixture.nativeElement.textContent).not.toContain('Carregando orçamentos');
  });

  it('permite tentar novamente após resposta incompatível da API', () => {
    http.expectOne(`${API_URL}/quotes`).flush({ items: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os orçamentos');

    const tela: HTMLElement = fixture.nativeElement;
    const botoes = Array.from(tela.querySelectorAll('button'));
    botoes.find((botao) => botao.textContent?.includes('Tentar novamente'))!.click();
    http.expectOne(`${API_URL}/quotes`).flush([orcamento]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('EAQ-1');
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar os orçamentos');
  });

  it('separa pendente de aceite e finalizado sem adivinhar status desconhecido', () => {
    http.expectOne(`${API_URL}/quotes`).flush([
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

  it('abre o status pela etapa normalizada e navega ao confirmar pelo botão da lista', () => {
    http.expectOne(`${API_URL}/quotes`).flush([orcamento]);
    fixture.detectChanges();
    openDialog.and.returnValue({ afterClosed: () => of('confirmed') });
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    buttons.find((button) => button.textContent?.includes('Verificar Status'))!.click();

    expect(openDialog).toHaveBeenCalledWith(jasmine.any(Function), jasmine.objectContaining({
      proposalId: 'EAQ-1', titulo: 'Status da Proposta', currentIndex: 3,
      messageTitle: 'Aguardando sua revisão', callToAction: 'Ver o orçamento',
    }), 'Status da Proposta EAQ-1');
    expect(navigate).toHaveBeenCalledWith(['/cliente/orcamentos', '1']);
  });

  it('mostra o recorte das amostras mesmo quando o status recebido é Em Andamento', () => {
    http.expectOne(`${API_URL}/quotes`).flush([
      { ...orcamento, stage: 'Fechado Ganho', status: 'Em Andamento' },
    ]);
    fixture.componentInstance.openStatusDialog(fixture.componentInstance.orcamentos()[0]);

    expect(openDialog.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
      titulo: 'Etapas da Análise Técnica das amostras', currentIndex: 0,
      steps: [
        { key: 'recebido', label: 'Recebido', icon: 'inbox' },
        { key: 'protocolado', label: 'Protocolado', icon: 'assignment_turned_in' },
        { key: 'em-execucao', label: 'Em execução', icon: 'science' },
        { key: 'incompleto', label: 'Incompleto', icon: 'warning_amber' },
        { key: 'completo', label: 'Completo', icon: 'done_all' },
      ],
    }));
    expect(openDialog.calls.mostRecent().args[1].protocolo).toBeUndefined();
    expect(openDialog.calls.mostRecent().args[1].contato).toBeUndefined();
  });

  it('mostra encerramento para orçamento cancelado sem pedir revisão', () => {
    http.expectOne(`${API_URL}/quotes`).flush([
      { ...orcamento, stage: 'Fechado Perdido', status: 'Cancelado' },
    ]);
    fixture.componentInstance.openStatusDialog(fixture.componentInstance.orcamentos()[0]);

    expect(openDialog.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
      titulo: 'Solicitação encerrada',
      message: 'Esta solicitação foi encerrada e não avança mais na linha de etapas.',
      callToAction: undefined,
    }));
  });

  it('informa que o acompanhamento está indisponível para etapa desconhecida', () => {
    http.expectOne(`${API_URL}/quotes`).flush([
      { ...orcamento, stage: 'Aceite em revisão', status: 'Aceite em revisão' },
    ]);
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture.componentInstance.openStatusDialog(fixture.componentInstance.orcamentos()[0]);

    expect(openDialog).not.toHaveBeenCalled();
    expect(snackBar).toHaveBeenCalledWith(
      'O acompanhamento detalhado ainda não está disponível.', 'Fechar', { duration: 6000 },
    );
  });
});
