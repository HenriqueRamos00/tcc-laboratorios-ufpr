import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AppDialogService } from '@/app/services/app-dialog.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';

import { AcceptanceTermsComponent } from './acceptance-terms.component';
import { ConfirmAcceptanceDialogComponent } from './confirm-acceptance-dialog.component';

// Dublê da porta de diálogo: guarda o que foi aberto e devolve a resposta que o
// caso de teste quiser, sem depender do overlay do Material.
class DialogSpy {
  answer: boolean | undefined = true;
  openedWith: { component: unknown; data: unknown }[] = [];

  open(component: unknown, data: unknown) {
    this.openedWith.push({ component, data });
    return { afterClosed: () => of(this.answer) };
  }
}

function fillEveryField(screen: HTMLElement): void {
  for (const field of screen.querySelectorAll('input')) {
    if (field.type === 'radio' || field.type === 'checkbox') continue;
    field.value =
      field.type === 'date'
        ? '2026-10-19'
        : field.type === 'email'
          ? 'cliente@exemplo.com'
          : 'preenchido';
    field.dispatchEvent(new Event('input'));
  }
}

describe('AcceptanceTermsComponent', () => {
  let dialogs: DialogSpy;
  let accept: jasmine.Spy;

  function render(): { fixture: ComponentFixture<AcceptanceTermsComponent>; screen: HTMLElement } {
    dialogs = new DialogSpy();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: MatSnackBar, useValue: { open: () => {} } },
        { provide: AppDialogService, useValue: dialogs },
      ],
    });
    accept = spyOn(TestBed.inject(QuoteDecisionService), 'aceitar').and.returnValue(
      of({ proposalId: 'quote-1', status: 'aprovado-pelo-cliente' as const, recordedAt: new Date() }),
    );

    const fixture = TestBed.createComponent(AcceptanceTermsComponent);
    fixture.componentRef.setInput('id', 'quote-1');
    fixture.detectChanges();

    return { fixture, screen: fixture.nativeElement as HTMLElement };
  }

  function submit(fixture: ComponentFixture<AcceptanceTermsComponent>, screen: HTMLElement): void {
    fillEveryField(screen);
    fixture.detectChanges();
    (screen.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  // O aceite é a última tela antes de a caixa sair da empresa do cliente, e é
  // onde o lembrete ainda muda o que vai escrito nela.
  it('lembra de identificar a caixa antes do botão que envia o aceite', () => {
    const { screen } = render();
    const notice = screen.querySelector('app-sample-shipping-notice');
    const button = screen.querySelector('button[type="submit"]');
    if (!notice) throw new Error('O termo de aceite não mostra o aviso de envio da amostra');
    if (!button) throw new Error('O termo de aceite não tem botão de envio');

    expect(notice.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  // O relatório passou a ser entregue pelo portal, então não há mais via
  // impressa para endereçar e o termo perdeu a seção que pedia esse endereço.
  it('não pede endereço de envio do relatório', () => {
    const { screen } = render();
    const legends = [...screen.querySelectorAll('legend')].map((legend) => legend.textContent ?? '');

    expect(legends.some((text) => text.includes('envio do relatório'))).toBeFalse();
    expect(legends.some((text) => text.includes('1. Endereço para cobrança'))).toBeTrue();
    expect(legends.some((text) => text.includes('2. Aprovação'))).toBeTrue();
  });

  it('não envia o aceite enquanto o cliente não confirma', () => {
    const { fixture, screen } = render();
    dialogs.answer = false;

    submit(fixture, screen);

    expect(dialogs.openedWith.length).toBe(1);
    expect(dialogs.openedWith[0].component).toBe(ConfirmAcceptanceDialogComponent);
    expect(dialogs.openedWith[0].data).toEqual({ proposalCode: 'quote-1' });
    expect(accept).not.toHaveBeenCalled();
  });

  it('envia o aceite depois da confirmação', () => {
    const { fixture, screen } = render();
    dialogs.answer = true;

    submit(fixture, screen);

    expect(accept).toHaveBeenCalledTimes(1);
  });

  // Confirmar um termo incompleto faria o cliente revisar na janela um
  // formulário que o próprio portal ainda vai recusar.
  it('não abre a confirmação com o termo incompleto', () => {
    const { fixture, screen } = render();

    (screen.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(dialogs.openedWith.length).toBe(0);
    expect(accept).not.toHaveBeenCalled();
  });
});
