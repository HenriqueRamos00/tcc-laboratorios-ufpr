import { Clipboard } from '@angular/cdk/clipboard';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { API_URL } from '@/app/environment/env';
import { indiceDaEtapa, LINHA_DE_ETAPAS } from '@/app/model/analysis-pipeline';

import {
  ProposalStatusDialogComponent,
  type ProposalStatusDialogData,
} from './proposal-status-dialog.component';

const CONTATO = 'atendimento@exemplo.com.br';
const ENDERECO_TEMPORARIO = 'blob:orcamento-de-teste';

const DADOS: ProposalStatusDialogData = {
  proposalId: 'EAQ_2026_45496_V_1',
  quoteId: 'quote-15',
  titulo: 'Etapas da Análise Técnica das amostras',
  steps: LINHA_DE_ETAPAS.map((etapa) => ({
    key: etapa.chave,
    label: etapa.rotulo,
    icon: etapa.icone,
  })),
  currentIndex: indiceDaEtapa('protocolado'),
  hasProposal: true,
  protocolo: 'L-EAQ_2026_45496_V_1-X',
  contato: CONTATO,
};

function montarModal(dados: ProposalStatusDialogData = DADOS, copiaFunciona = true) {
  const copiados: string[] = [];

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MAT_DIALOG_DATA, useValue: dados },
      { provide: MatDialogRef, useValue: { close: () => {} } },
      {
        provide: Clipboard,
        useValue: {
          copy: (texto: string) => {
            copiados.push(texto);
            return copiaFunciona;
          },
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(ProposalStatusDialogComponent);
  fixture.detectChanges();

  return { fixture, tela: fixture.nativeElement as HTMLElement, copiados };
}

function caixaDoContato(tela: HTMLElement): HTMLInputElement {
  return tela.querySelector('.status-dialog-contact__value') as HTMLInputElement;
}

describe('ProposalStatusDialogComponent, contato e download', () => {
  // O cliente precisa conseguir selecionar o endereço com o mouse quando a
  // cópia automática não estiver disponível, e não pode conseguir alterá-lo:
  // campo desabilitado atenderia a segunda parte e quebraria a primeira.
  it('mostra o contato numa caixa selecionável que não aceita edição', () => {
    const { tela } = montarModal();
    const caixa = caixaDoContato(tela);

    expect(caixa.value).toBe(CONTATO);
    expect(caixa.readOnly).toBeTrue();
    expect(caixa.disabled).toBeFalse();
  });

  it('o botão ao redor da caixa copia o endereço do contato', () => {
    const { tela, copiados } = montarModal();

    (tela.querySelector('.status-dialog-contact__copy') as HTMLButtonElement).click();

    expect(copiados).toEqual([CONTATO]);
  });

  it('quando a área de transferência recusa, manda copiar pela caixa', () => {
    const { fixture, tela } = montarModal(DADOS, false);

    (tela.querySelector('.status-dialog-contact__copy') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(tela.textContent).toContain('Selecione o endereço na caixa');
  });

  it('não mostra a caixa de contato nas etapas anteriores ao laboratório', () => {
    const { tela } = montarModal({ ...DADOS, contato: undefined, protocolo: undefined });

    expect(tela.querySelector('.status-dialog-contact')).toBeNull();
    expect(tela.querySelector('.status-dialog-download')).not.toBeNull();
  });

  // Em qualificação e em análise pela área ninguém escreveu a proposta ainda:
  // oferecer o download ali prometeria um documento que não existe.
  it('não oferece download enquanto a proposta não foi escrita', () => {
    const { tela } = montarModal({ ...DADOS, hasProposal: false });

    expect(tela.querySelector('.status-dialog-download')).toBeNull();
    expect(tela.querySelector('.status-dialog-contact')).not.toBeNull();
  });

  // O cliente lê a etapa no stepper e descobre que precisa despachar a amostra
  // na mesma janela: a orientação de envio não pode ficar só na listagem.
  it('mostra a orientação de envio na etapa que pede o despacho da amostra', () => {
    const comEnvio = montarModal({ ...DADOS, shipping: 'address-and-labeling' });
    const semEnvio = montarModal({ ...DADOS, shipping: undefined });

    expect(comEnvio.tela.querySelector('app-sample-shipping-notice')).not.toBeNull();
    expect(semEnvio.tela.querySelector('app-sample-shipping-notice')).toBeNull();
  });

  it('baixa o orçamento do modal pelo identificador do próprio orçamento', fakeAsync(() => {
    const criar = spyOn(URL, 'createObjectURL').and.returnValue(ENDERECO_TEMPORARIO);
    const revogar = spyOn(URL, 'revokeObjectURL');
    const clicar = spyOn(HTMLAnchorElement.prototype, 'click');
    const { fixture, tela } = montarModal();

    (tela.querySelector('.status-dialog-download') as HTMLButtonElement).click();
    fixture.detectChanges();

    const pedido = TestBed.inject(HttpTestingController).expectOne(
      `${API_URL}/api/quotes/quote-15/document`,
    );
    expect(pedido.request.responseType).toBe('blob');
    pedido.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
    fixture.detectChanges();

    expect(criar.calls.mostRecent().args[0] instanceof Blob).toBeTrue();
    expect(clicar).toHaveBeenCalled();
    expect((clicar.calls.mostRecent().object as HTMLAnchorElement).download).toBe(
      'orcamento-EAQ_2026_45496_V_1.pdf',
    );

    tick();
    expect(revogar).toHaveBeenCalledWith(ENDERECO_TEMPORARIO);
  }));

  it('avisa quando a proposta ainda não tem PDF, em vez de salvar arquivo nenhum', () => {
    const criar = spyOn(URL, 'createObjectURL');
    const { fixture, tela } = montarModal();

    (tela.querySelector('.status-dialog-download') as HTMLButtonElement).click();
    TestBed.inject(HttpTestingController)
      .expectOne(`${API_URL}/api/quotes/quote-15/document`)
      .flush(new Blob(['sem documento']), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(tela.textContent).toContain('ainda não está disponível');
    expect(criar).not.toHaveBeenCalled();
  });
});
