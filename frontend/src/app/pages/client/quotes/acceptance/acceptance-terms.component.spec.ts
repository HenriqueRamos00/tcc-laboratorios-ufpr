import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { AcceptanceTermsComponent } from './acceptance-terms.component';

function montarTermo() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: MatSnackBar, useValue: { open: () => {} } }],
  });

  const fixture = TestBed.createComponent(AcceptanceTermsComponent);
  fixture.componentRef.setInput('id', 'quote-1');
  fixture.detectChanges();

  return fixture.nativeElement as HTMLElement;
}

describe('AcceptanceTermsComponent', () => {
  // O aceite é a última tela antes de a caixa sair da empresa do cliente, e é
  // onde o lembrete ainda muda o que vai escrito nela.
  it('lembra de identificar a caixa antes do botão que envia o aceite', () => {
    const tela = montarTermo();
    const aviso = tela.querySelector('app-sample-shipping-notice');
    const enviar = tela.querySelector('button[type="submit"]');
    if (!aviso) throw new Error('O termo de aceite não mostra o aviso de envio da amostra');
    if (!enviar) throw new Error('O termo de aceite não tem botão de envio');

    const ordem = aviso.compareDocumentPosition(enviar);
    expect(ordem & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});
