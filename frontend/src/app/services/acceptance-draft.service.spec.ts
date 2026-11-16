import { TestBed } from '@angular/core/testing';

import type { AcceptanceFormValue } from '@/app/model/acceptance';

import { ACCEPTANCE_DRAFT_VALIDITY_DAYS, AcceptanceDraftService } from './acceptance-draft.service';
import { LS_TOKEN } from './login.service';

const PREFIX = 'lactec.acceptance-draft';
const DAY_MS = 24 * 60 * 60 * 1000;

function terms(company = 'EMPRESA DE ENERGIA'): AcceptanceFormValue {
  return {
    billing: {
      company, cnpj: '30960572000183', stateRegistration: '', street: 'Rua 1', city: 'Curitiba',
      state: 'PR', district: '', postalCode: '80000000', contact: 'Marina', phone: '41999999999',
      invoiceEmails: ['financeiro@empresa.com', ''],
    },
    approval: {
      requesterName: 'Marina', responsibleName: '', jobTitle: '', department: '',
      date: '', allowsResultsByEmail: true, resultsEmail: '',
    },
  };
}

function draftKeys(storage: Storage): string[] {
  return Object.keys(storage).filter((key) => key.startsWith(PREFIX)).sort();
}

function clearEverything(): void {
  for (const storage of [localStorage, sessionStorage]) {
    for (const key of [...draftKeys(storage), LS_TOKEN]) storage.removeItem(key);
  }
}

describe('AcceptanceDraftService', () => {
  let drafts: AcceptanceDraftService;

  beforeEach(() => {
    clearEverything();
    drafts = TestBed.inject(AcceptanceDraftService);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    clearEverything();
  });

  it('devolve o rascunho guardado para o mesmo usuário e orçamento', () => {
    const savedAt = drafts.save('42', 'quote-1', terms());

    const draft = drafts.read('42', 'quote-1');

    expect(savedAt).toEqual(jasmine.any(Date));
    expect(draft?.terms).toEqual(terms());
    expect(draft?.savedAt).toEqual(savedAt!);
  });

  it('separa os rascunhos por usuário e por orçamento', () => {
    drafts.save('42', 'quote-1', terms());

    expect(drafts.read('77', 'quote-1')).toBeNull();
    expect(drafts.read('42', 'quote-2')).toBeNull();
    expect(draftKeys(sessionStorage)).toEqual([`${PREFIX}:42:quote-1`]);
  });

  it('não confunde usuário e orçamento cujos identificadores têm o separador da chave', () => {
    drafts.save('42:quote', '1', terms('A'));
    drafts.save('42', 'quote:1', terms('B'));

    expect(drafts.read('42:quote', '1')?.terms.billing.company).toBe('A');
    expect(drafts.read('42', 'quote:1')?.terms.billing.company).toBe('B');
  });

  it('não guarda nem lê nada sem usuário identificado', () => {
    expect(drafts.save(null, 'quote-1', terms())).toBeNull();
    expect(drafts.read(null, 'quote-1')).toBeNull();
    expect(draftKeys(sessionStorage)).toEqual([]);
  });

  it('guarda na aba quando a sessão é só da aba e no navegador quando o cliente pediu para manter conectado', () => {
    drafts.save('42', 'quote-1', terms());
    expect(draftKeys(sessionStorage).length).toBe(1);
    expect(draftKeys(localStorage)).toEqual([]);

    localStorage.setItem(LS_TOKEN, 'token-de-teste');
    drafts.save('42', 'quote-2', terms());

    expect(draftKeys(localStorage)).toEqual([`${PREFIX}:42:quote-2`]);
  });

  it('apaga e não devolve o rascunho vencido', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 10, 1, 9, 0));
    drafts.save('42', 'quote-1', terms());

    jasmine.clock().mockDate(new Date(2026, 10, 1 + ACCEPTANCE_DRAFT_VALIDITY_DAYS, 8, 59));
    expect(drafts.read('42', 'quote-1')).not.toBeNull();

    jasmine.clock().mockDate(new Date(2026, 10, 1 + ACCEPTANCE_DRAFT_VALIDITY_DAYS, 9, 1));
    expect(drafts.read('42', 'quote-1')).toBeNull();
    expect(draftKeys(sessionStorage)).toEqual([]);
  });

  for (const [caso, guardado] of [
    ['texto que não é JSON', '{nao e json'],
    ['outra versão do rascunho', JSON.stringify({ version: 0, savedAt: Date.now(), terms: terms() })],
    ['data de gravação no futuro', JSON.stringify({ version: 1, savedAt: Date.now() + DAY_MS, terms: terms() })],
    ['termo sem o bloco de aprovação', JSON.stringify({ version: 1, savedAt: Date.now(), terms: { billing: terms().billing } })],
    ['campo de texto com outro tipo', JSON.stringify({ version: 1, savedAt: Date.now(), terms: { ...terms(), approval: { ...terms().approval, date: 20261115 } } })],
    ['e-mails de NFS-e num texto só', JSON.stringify({ version: 1, savedAt: Date.now(), terms: { ...terms(), billing: { ...terms().billing, invoiceEmails: 'a@b.com' } } })],
    ['menos de dois e-mails de NFS-e', JSON.stringify({ version: 1, savedAt: Date.now(), terms: { ...terms(), billing: { ...terms().billing, invoiceEmails: ['a@b.com'] } } })],
  ] as const) {
    it(`descarta em vez de interpretar: ${caso}`, () => {
      const key = `${PREFIX}:42:quote-1`;
      sessionStorage.setItem(key, guardado);

      expect(drafts.read('42', 'quote-1')).toBeNull();
      expect(sessionStorage.getItem(key)).toBeNull();
    });
  }

  it('segue sem rascunho quando o navegador bloqueia o armazenamento', () => {
    spyOn(Storage.prototype, 'setItem').and.throwError('QuotaExceededError');
    spyOn(Storage.prototype, 'getItem').and.throwError('SecurityError');
    spyOn(Storage.prototype, 'key').and.throwError('SecurityError');

    expect(drafts.save('42', 'quote-1', terms())).toBeNull();
    expect(drafts.read('42', 'quote-1')).toBeNull();
    expect(() => drafts.discard('quote-1')).not.toThrow();
    expect(() => drafts.keepOnlyValidDraftsOf('42')).not.toThrow();
    expect(() => drafts.clear()).not.toThrow();
  });

  it('descarta o rascunho de um orçamento de qualquer usuário e de qualquer área', () => {
    drafts.save('42', 'quote-1', terms());
    drafts.save('42', 'quote-2', terms());
    localStorage.setItem(`${PREFIX}:77:quote-1`, JSON.stringify({ version: 1, savedAt: Date.now(), terms: terms() }));

    drafts.discard('quote-1');

    expect(draftKeys(sessionStorage)).toEqual([`${PREFIX}:42:quote-2`]);
    expect(draftKeys(localStorage)).toEqual([]);
  });

  it('ao identificar o usuário, apaga os rascunhos de outras pessoas e os que venceram', () => {
    const vencido = { version: 1, savedAt: Date.now() - (ACCEPTANCE_DRAFT_VALIDITY_DAYS + 1) * DAY_MS, terms: terms() };
    drafts.save('42', 'quote-1', terms());
    drafts.save('77', 'quote-1', terms());
    sessionStorage.setItem(`${PREFIX}:42:quote-9`, JSON.stringify(vencido));
    localStorage.setItem(`${PREFIX}:77:quote-3`, JSON.stringify({ ...vencido, savedAt: Date.now() }));
    localStorage.setItem('lactec.shell.collapsed', '1');

    drafts.keepOnlyValidDraftsOf('42');

    expect(draftKeys(sessionStorage)).toEqual([`${PREFIX}:42:quote-1`]);
    expect(draftKeys(localStorage)).toEqual([]);
    expect(localStorage.getItem('lactec.shell.collapsed')).toBe('1');
    localStorage.removeItem('lactec.shell.collapsed');
  });

  it('ao sair da conta, apaga todos os rascunhos e só eles', () => {
    drafts.save('42', 'quote-1', terms());
    localStorage.setItem(`${PREFIX}:77:quote-3`, JSON.stringify({ version: 1, savedAt: Date.now(), terms: terms() }));
    sessionStorage.setItem('outra-chave', 'fica');

    drafts.clear();

    expect(draftKeys(sessionStorage)).toEqual([]);
    expect(draftKeys(localStorage)).toEqual([]);
    expect(sessionStorage.getItem('outra-chave')).toBe('fica');
    sessionStorage.removeItem('outra-chave');
  });
});
