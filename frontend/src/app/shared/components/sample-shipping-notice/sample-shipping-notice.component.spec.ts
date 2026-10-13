import { TestBed } from '@angular/core/testing';

import { LABORATORY_ADDRESS, type ShippingGuidance } from '@/app/model/sample-shipping';

import { SampleShippingNoticeComponent } from './sample-shipping-notice.component';

const CODIGO = 'EAQ_2026_45487_V_1';

function montarAviso(guidance?: ShippingGuidance) {
  const fixture = TestBed.createComponent(SampleShippingNoticeComponent);
  fixture.componentRef.setInput('proposalCode', CODIGO);
  if (guidance) fixture.componentRef.setInput('guidance', guidance);
  fixture.detectChanges();

  return fixture.nativeElement as HTMLElement;
}

describe('SampleShippingNoticeComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  // A etiqueta sem o código não resolve o problema do recebimento: a caixa
  // continua sem dizer a qual orçamento pertence.
  it('a etiqueta pedida leva o código da proposta', () => {
    const tela = montarAviso();

    expect(tela.textContent).toContain(CODIGO);
  });

  // O endereço serve a quem ainda vai despachar. Repeti-lo depois do envio
  // convida o cliente a mandar a amostra outra vez.
  it('o endereço do laboratório só aparece enquanto a amostra não saiu', () => {
    const antesDoEnvio = montarAviso('address-and-labeling');
    const depoisDoEnvio = montarAviso('labeling');

    for (const linha of LABORATORY_ADDRESS) {
      expect(antesDoEnvio.textContent).toContain(linha);
      expect(depoisDoEnvio.textContent).not.toContain(linha);
    }
  });
});
