import { provideEnvironmentNgxMask } from 'ngx-mask';
import type { AcceptanceTerms } from '@/app/model/acceptance';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';

import { AcceptanceDraftService } from '@/app/services/acceptance-draft.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService, LS_TOKEN } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';

import { AcceptanceTermsComponent, DRAFT_SAVE_DELAY_MS } from './acceptance-terms.component';
import { ConfirmAcceptanceDialogComponent } from './confirm-acceptance-dialog.component';
import { LeaveAcceptanceDialogComponent } from './leave-acceptance-dialog.component';
import { ResumeDraftDialogComponent } from './resume-draft-dialog.component';

// Dublê da porta de diálogo: guarda o que foi aberto e devolve a resposta que o
// caso de teste quiser, sem depender do overlay do Material.
class DialogSpy {
  answer: boolean | undefined = true;
  resumeAnswer: 'resume' | 'start-over' | undefined = 'resume';
  leaveAnswer: 'keep-draft' | 'discard' | undefined = 'keep-draft';
  openedWith: { component: unknown; data: unknown }[] = [];

  open(component: unknown, data: unknown) {
    this.openedWith.push({ component, data });
    const answer =
      component === ResumeDraftDialogComponent
        ? this.resumeAnswer
        : component === LeaveAcceptanceDialogComponent
          ? this.leaveAnswer
          : this.answer;
    return { afterClosed: () => of(answer) };
  }

  dataOf<T>(component: unknown): T | undefined {
    return this.openedWith.find((opened) => opened.component === component)?.data as T | undefined;
  }
}

const OWNER = '42';
const DRAFT_KEY_PREFIX = 'lactec.acceptance-draft';

const FIELD_VALUES: Record<string, string> = {
  CNPJ: '12.abc.345/01de-35',
  UF: 'pr',
  CEP: '80000-000',
  Telefone: '(41) 99999-9999',
  'E-mail 1 para NFS-e': 'financeiro@exemplo.com',
  'E-mail 2 para NFS-e': 'fiscal@exemplo.com',
  'E-mail 3 para NFS-e': 'contabil@exemplo.com',
};

function labelOf(field: HTMLInputElement): string {
  return field.closest('label')?.querySelector('span')?.textContent?.trim() ?? '';
}

function fieldLabelled(screen: HTMLElement, label: string): HTMLInputElement {
  const field = [...screen.querySelectorAll('input')].find((input) => labelOf(input) === label);
  if (!field) throw new Error(`O termo de aceite não tem o campo "${label}"`);
  return field;
}

function type(screen: HTMLElement, label: string, value: string): void {
  const field = fieldLabelled(screen, label);
  field.value = value;
  field.dispatchEvent(new Event('input'));
  field.dispatchEvent(new Event('blur'));
}

function errorOf(screen: HTMLElement, label: string): string {
  return (
    fieldLabelled(screen, label).closest('label')?.querySelector('[role="alert"]')?.textContent?.trim() ?? ''
  );
}

function fillEveryField(screen: HTMLElement): void {
  for (const field of screen.querySelectorAll('input')) {
    if (field.type === 'radio' || field.type === 'checkbox') continue;
    field.value =
      FIELD_VALUES[labelOf(field)] ??
      (field.type === 'date'
        ? '2026-10-19'
        : field.type === 'email'
          ? 'cliente@exemplo.com'
          : 'preenchido');
    field.dispatchEvent(new Event('input'));
  }
}

function buttonLabelled(screen: HTMLElement, text: string): HTMLButtonElement | undefined {
  return [...screen.querySelectorAll('button')].find((button) => button.textContent?.trim() === text);
}

function closeTheTab(): Event {
  const closing = new Event('beforeunload', { cancelable: true });
  const runnerHook = window.onbeforeunload;
  window.onbeforeunload = null;
  try {
    window.dispatchEvent(closing);
  } finally {
    window.onbeforeunload = runnerHook;
  }
  return closing;
}

function storedDraftKeys(): string[] {
  return [localStorage, sessionStorage].flatMap((storage) =>
    Object.keys(storage).filter((key) => key.startsWith(DRAFT_KEY_PREFIX)),
  );
}

function clearStoredDrafts(): void {
  for (const storage of [localStorage, sessionStorage]) {
    for (const key of Object.keys(storage)) {
      if (key.startsWith(DRAFT_KEY_PREFIX)) storage.removeItem(key);
    }
  }
}

describe('AcceptanceTermsComponent', () => {
  let dialogs: DialogSpy;
  let accept: jasmine.Spy;
  let owner: string;

  beforeEach(() => {
    clearStoredDrafts();
    owner = OWNER;
    dialogs = new DialogSpy();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideEnvironmentNgxMask(),
        { provide: MatSnackBar, useValue: { open: () => {} } },
        { provide: AppDialogService, useValue: dialogs },
        { provide: LoginService, useValue: { currentIdentity: () => ({ id: owner }) } },
      ],
    });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    accept = spyOn(TestBed.inject(QuoteDecisionService), 'aceitar').and.returnValue(
      of({ proposalId: 'quote-1', status: 'aprovado-pelo-cliente' as const, recordedAt: new Date() }),
    );
  });

  afterEach(clearStoredDrafts);

  function render(): { fixture: ComponentFixture<AcceptanceTermsComponent>; screen: HTMLElement } {
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

  function leaveADraftBehind(): void {
    const { fixture, screen } = render();
    type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
    type(screen, 'CNPJ', '12.abc.345/01de-35');
    type(screen, 'E-mail 1 para NFS-e', 'financeiro@exemplo.com');
    buttonLabelled(screen, 'Adicionar outro e-mail')!.click();
    fixture.detectChanges();
    type(screen, 'E-mail 3 para NFS-e', 'contabil@exemplo.com');
    fixture.destroy();
    dialogs.openedWith = [];
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

  it('descarta o rascunho e leva ao orçamento quando ele já tem decisão registrada', () => {
    const { fixture, screen } = render();
    dialogs.answer = true;
    accept.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    submit(fixture, screen);

    expect(storedDraftKeys()).toEqual([]);
    expect(fixture.componentInstance.hasUnsentChanges()).toBeFalse();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/cliente/quotes', 'quote-1']);
  });

  it('envia os campos com máscara sem pontuação e a sigla da UF em caixa alta', () => {
    const { fixture, screen } = render();
    dialogs.answer = true;

    submit(fixture, screen);

    const billing = (accept.calls.mostRecent().args[0] as AcceptanceTerms).billing;
    expect([billing.cnpj, billing.state, billing.postalCode, billing.phone]).toEqual([
      '12ABC34501DE35',
      'PR',
      '80000000',
      '41999999999',
    ]);
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

  describe('e-mails para envio de NFS-e', () => {
    it('pede dois endereços em campos separados e os envia como lista', () => {
      const { fixture, screen } = render();

      expect(fieldLabelled(screen, 'E-mail 1 para NFS-e').type).toBe('email');
      expect(fieldLabelled(screen, 'E-mail 2 para NFS-e').type).toBe('email');

      submit(fixture, screen);

      const billing = (accept.calls.mostRecent().args[0] as AcceptanceTerms).billing;
      expect(billing.invoiceEmails).toEqual(['financeiro@exemplo.com', 'fiscal@exemplo.com']);
    });

    it('não aceita o termo com um endereço só', () => {
      const { fixture, screen } = render();
      fillEveryField(screen);
      type(screen, 'E-mail 2 para NFS-e', '');
      fixture.detectChanges();

      (screen.querySelector('button[type="submit"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(errorOf(screen, 'E-mail 2 para NFS-e')).toBe('Campo obrigatório');
      expect(dialogs.openedWith.length).toBe(0);
    });

    it('avisa quando o endereço não é um e-mail', () => {
      const { fixture, screen } = render();

      type(screen, 'E-mail 1 para NFS-e', 'financeiro.exemplo.com');
      fixture.detectChanges();

      expect(errorOf(screen, 'E-mail 1 para NFS-e')).toBe('Informe um e-mail válido');
    });

    it('não aceita o mesmo endereço duas vezes, nem mudando maiúsculas', () => {
      const { fixture, screen } = render();
      fillEveryField(screen);
      type(screen, 'E-mail 2 para NFS-e', 'Financeiro@Exemplo.com');
      fixture.detectChanges();

      (screen.querySelector('button[type="submit"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(errorOf(screen, 'E-mail 1 para NFS-e')).toBe('');
      expect(errorOf(screen, 'E-mail 2 para NFS-e')).toBe('Este e-mail já foi informado em outro campo');
      expect(dialogs.openedWith.length).toBe(0);
    });

    it('tira o aviso de repetido quando o cliente corrige o primeiro endereço', () => {
      const { fixture, screen } = render();
      type(screen, 'E-mail 1 para NFS-e', 'fiscal@exemplo.com');
      type(screen, 'E-mail 2 para NFS-e', 'fiscal@exemplo.com');
      fixture.detectChanges();
      expect(errorOf(screen, 'E-mail 2 para NFS-e')).toBe('Este e-mail já foi informado em outro campo');

      type(screen, 'E-mail 1 para NFS-e', 'financeiro@exemplo.com');
      fixture.detectChanges();

      expect(errorOf(screen, 'E-mail 2 para NFS-e')).toBe('');
    });

    it('deixa acrescentar endereços até o limite e remover só os que passam de dois', () => {
      const { fixture, screen } = render();

      expect(buttonLabelled(screen, 'Remover')).toBeUndefined();

      for (let extra = 0; extra < 3; extra++) {
        buttonLabelled(screen, 'Adicionar outro e-mail')!.click();
        fixture.detectChanges();
      }

      expect(screen.querySelectorAll('input[type="email"]').length).toBe(6);
      expect(buttonLabelled(screen, 'Adicionar outro e-mail')).toBeUndefined();
      expect(screen.querySelectorAll('button[aria-label^="Remover o e-mail"]').length).toBe(3);

      (screen.querySelector('button[aria-label="Remover o e-mail 4 para NFS-e"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(screen.querySelectorAll('input[type="email"]').length).toBe(5);
      expect(buttonLabelled(screen, 'Adicionar outro e-mail')).toBeDefined();
    });

    it('envia os endereços extras junto com os dois obrigatórios', () => {
      const { fixture, screen } = render();
      buttonLabelled(screen, 'Adicionar outro e-mail')!.click();
      fixture.detectChanges();

      submit(fixture, screen);

      const billing = (accept.calls.mostRecent().args[0] as AcceptanceTerms).billing;
      expect(billing.invoiceEmails).toEqual([
        'financeiro@exemplo.com',
        'fiscal@exemplo.com',
        'contabil@exemplo.com',
      ]);
    });
  });

  describe('rascunho do termo', () => {
    it('guarda o que foi preenchido depois de uma pausa na digitação, por usuário e orçamento', fakeAsync(() => {
      const { fixture, screen } = render();

      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
      tick(DRAFT_SAVE_DELAY_MS - 1);
      expect(storedDraftKeys()).toEqual([]);

      tick(1);
      fixture.detectChanges();

      expect(storedDraftKeys()).toEqual([`${DRAFT_KEY_PREFIX}:${OWNER}:quote-1`]);
      expect(screen.textContent).toContain('Rascunho guardado neste navegador');
    }));

    it('não deixa rascunho de um termo que continua em branco', () => {
      const { fixture } = render();

      fixture.destroy();

      expect(storedDraftKeys()).toEqual([]);
    });

    it('guarda o rascunho ao sair da tela sem esperar a pausa', () => {
      const { fixture, screen } = render();
      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');

      fixture.destroy();

      expect(TestBed.inject(AcceptanceDraftService).read(OWNER, 'quote-1')?.terms.billing.company)
        .toBe('EMPRESA DE ENERGIA');
    });

    it('pergunta se o cliente quer continuar e devolve os campos, inclusive o e-mail extra', fakeAsync(() => {
      leaveADraftBehind();
      dialogs.resumeAnswer = 'resume';

      const { fixture, screen } = render();
      tick(DRAFT_SAVE_DELAY_MS);
      fixture.detectChanges();

      expect(dialogs.openedWith.map((opened) => opened.component)).toEqual([ResumeDraftDialogComponent]);
      expect(dialogs.dataOf<{ proposalCode: string }>(ResumeDraftDialogComponent)?.proposalCode).toBe('quote-1');
      expect(fieldLabelled(screen, 'Empresa').value).toBe('EMPRESA DE ENERGIA');
      expect(fieldLabelled(screen, 'CNPJ').value).toBe('12.ABC.345/01DE-35');
      expect(fieldLabelled(screen, 'E-mail 3 para NFS-e').value).toBe('contabil@exemplo.com');
      expect(screen.querySelector('[role="alert"]')).toBeNull();
      expect(fixture.componentInstance.hasUnsentChanges()).toBeFalse();
    }));

    it('fechar a pergunta sem escolher continua o rascunho em vez de perdê-lo', () => {
      leaveADraftBehind();
      dialogs.resumeAnswer = undefined;

      const { screen } = render();

      expect(fieldLabelled(screen, 'Empresa').value).toBe('EMPRESA DE ENERGIA');
      expect(storedDraftKeys().length).toBe(1);
    });

    it('apaga o rascunho quando o cliente prefere começar em branco', () => {
      leaveADraftBehind();
      dialogs.resumeAnswer = 'start-over';

      const { fixture, screen } = render();

      expect(fieldLabelled(screen, 'Empresa').value).toBe('');
      expect(screen.querySelectorAll('input[type="email"]').length).toBe(3);
      expect(storedDraftKeys()).toEqual([]);
      expect(fixture.componentInstance.hasUnsentChanges()).toBeFalse();
    });

    it('não mostra a outro usuário o rascunho de quem preencheu', () => {
      leaveADraftBehind();
      owner = '77';

      const { screen } = render();

      expect(dialogs.openedWith).toEqual([]);
      expect(fieldLabelled(screen, 'Empresa').value).toBe('');
    });

    it('só considera alteração não enviada o que mudou desde que a tela abriu', () => {
      const { fixture, screen } = render();
      expect(fixture.componentInstance.hasUnsentChanges()).toBeFalse();

      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
      expect(fixture.componentInstance.hasUnsentChanges()).toBeTrue();

      type(screen, 'Empresa', '');
      expect(fixture.componentInstance.hasUnsentChanges()).toBeFalse();
    });

    it('ao sair, guardar o rascunho libera a navegação e mantém o que foi preenchido', async () => {
      const { fixture, screen } = render();
      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
      dialogs.leaveAnswer = 'keep-draft';

      const leaves = await firstValueFrom(fixture.componentInstance.confirmLeaving());
      fixture.destroy();

      expect(leaves).toBeTrue();
      expect(dialogs.dataOf(LeaveAcceptanceDialogComponent)).toEqual({ draftSaved: true, validityDays: 7 });
      expect(storedDraftKeys().length).toBe(1);
    });

    it('ao sair, descartar libera a navegação e não deixa rascunho para trás', async () => {
      const { fixture, screen } = render();
      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
      dialogs.leaveAnswer = 'discard';

      const leaves = await firstValueFrom(fixture.componentInstance.confirmLeaving());
      fixture.destroy();

      expect(leaves).toBeTrue();
      expect(storedDraftKeys()).toEqual([]);
    });

    it('ao sair, continuar editando segura a navegação', async () => {
      const { fixture, screen } = render();
      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
      dialogs.leaveAnswer = undefined;

      const leaves = await firstValueFrom(fixture.componentInstance.confirmLeaving());

      expect(leaves).toBeFalse();
      expect(fixture.componentInstance.hasUnsentChanges()).toBeTrue();
    });

    it('depois de enviar o aceite não pergunta ao sair nem regrava o rascunho', fakeAsync(() => {
      const { fixture, screen } = render();
      dialogs.answer = true;

      submit(fixture, screen);
      tick(DRAFT_SAVE_DELAY_MS);
      fixture.destroy();

      expect(accept).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.hasUnsentChanges()).toBeFalse();
      expect(storedDraftKeys()).toEqual([]);
    }));

    it('não segura o fechamento da aba quando o rascunho sobrevive a ela', () => {
      localStorage.setItem(LS_TOKEN, 'token-de-teste');
      try {
        const { screen } = render();
        type(screen, 'Empresa', 'EMPRESA DE ENERGIA');

        const closing = closeTheTab();

        expect(closing.defaultPrevented).toBeFalse();
        expect(storedDraftKeys().length).toBe(1);
      } finally {
        localStorage.removeItem(LS_TOKEN);
      }
    });

    it('pede a confirmação do navegador quando o rascunho morre junto com a aba', () => {
      const { screen } = render();
      type(screen, 'Empresa', 'EMPRESA DE ENERGIA');

      const closing = closeTheTab();

      expect(closing.defaultPrevented).toBeTrue();
      expect(storedDraftKeys().length).toBe(1);
    });

    describe('com o armazenamento do navegador bloqueado', () => {
      beforeEach(() => {
        spyOn(Storage.prototype, 'setItem').and.throwError('QuotaExceededError');
      });

      it('avisa na tela que o rascunho não foi guardado, sem quebrar o formulário', fakeAsync(() => {
        const { fixture, screen } = render();

        type(screen, 'Empresa', 'EMPRESA DE ENERGIA');
        tick(DRAFT_SAVE_DELAY_MS);
        fixture.detectChanges();

        expect(screen.querySelector('[role="status"]')?.textContent).toContain('não permitiu guardar o rascunho');
        expect(fieldLabelled(screen, 'Empresa').value).toBe('EMPRESA DE ENERGIA');
      }));

      it('diz no diálogo de saída que o preenchimento será perdido', async () => {
        const { fixture, screen } = render();
        type(screen, 'Empresa', 'EMPRESA DE ENERGIA');

        await firstValueFrom(fixture.componentInstance.confirmLeaving());

        expect(dialogs.dataOf(LeaveAcceptanceDialogComponent)).toEqual({ draftSaved: false, validityDays: 7 });
      });

      it('pede a confirmação do navegador antes de fechar a aba', () => {
        const { screen } = render();
        type(screen, 'Empresa', 'EMPRESA DE ENERGIA');

        const closing = closeTheTab();

        expect(closing.defaultPrevented).toBeTrue();
      });
    });
  });
});
