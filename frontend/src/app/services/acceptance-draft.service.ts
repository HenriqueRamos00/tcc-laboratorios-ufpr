import { Injectable } from '@angular/core';

import { INVOICE_EMAIL_COUNT, type AcceptanceFormValue } from '@/app/model/acceptance';

import { sessionStorageForToken } from './login.service';

const KEY_PREFIX = 'lactec.acceptance-draft';
const KEY_SEPARATOR = ':';
const DRAFT_VERSION = 1;
const DAY_MS = 24 * 60 * 60 * 1000;

export const ACCEPTANCE_DRAFT_VALIDITY_DAYS = 7;

const BILLING_TEXT_FIELDS = [
  'company',
  'cnpj',
  'stateRegistration',
  'street',
  'city',
  'state',
  'district',
  'postalCode',
  'contact',
  'phone',
] as const;

const APPROVAL_TEXT_FIELDS = [
  'requesterName',
  'responsibleName',
  'jobTitle',
  'department',
  'date',
  'resultsEmail',
] as const;

export interface AcceptanceDraft {
  readonly savedAt: Date;
  readonly terms: AcceptanceFormValue;
}

interface StoredDraft {
  readonly version: typeof DRAFT_VERSION;
  readonly savedAt: number;
  readonly terms: AcceptanceFormValue;
}

interface DraftKey {
  readonly owner: string;
  readonly proposalId: string;
}

@Injectable({ providedIn: 'root' })
export class AcceptanceDraftService {
  read(owner: string | null, proposalId: string): AcceptanceDraft | null {
    const storage = sessionArea();
    if (!owner || !storage) return null;

    const key = keyOf({ owner, proposalId });
    try {
      const stored = parse(storage.getItem(key));
      if (!stored) {
        storage.removeItem(key);
        return null;
      }
      return { savedAt: new Date(stored.savedAt), terms: stored.terms };
    } catch {
      return null;
    }
  }

  outlivesTheTab(): boolean {
    try {
      return sessionArea() === localStorage;
    } catch {
      return false;
    }
  }

  save(owner: string | null, proposalId: string, terms: AcceptanceFormValue): Date | null {
    const storage = sessionArea();
    if (!owner || !storage) return null;

    const stored: StoredDraft = { version: DRAFT_VERSION, savedAt: Date.now(), terms };
    try {
      storage.setItem(keyOf({ owner, proposalId }), JSON.stringify(stored));
      return new Date(stored.savedAt);
    } catch {
      return null;
    }
  }

  discard(proposalId: string): void {
    removeDrafts((key) => key.proposalId === proposalId);
  }

  keepOnlyValidDraftsOf(owner: string): void {
    removeDrafts((key, raw) => key.owner !== owner || !parse(raw));
  }

  clear(): void {
    removeDrafts(() => true);
  }
}

function keyOf(key: DraftKey): string {
  return [KEY_PREFIX, encodeURIComponent(key.owner), encodeURIComponent(key.proposalId)].join(
    KEY_SEPARATOR,
  );
}

function draftKeyOf(storageKey: string): DraftKey | null {
  const [prefix, owner, proposalId, ...rest] = storageKey.split(KEY_SEPARATOR);
  if (prefix !== KEY_PREFIX || !owner || !proposalId || rest.length > 0) return null;
  try {
    return { owner: decodeURIComponent(owner), proposalId: decodeURIComponent(proposalId) };
  } catch {
    return null;
  }
}

function sessionArea(): Storage | null {
  try {
    return sessionStorageForToken();
  } catch {
    return null;
  }
}

function everyArea(): Storage[] {
  const areas: Storage[] = [];
  for (const area of [() => localStorage, () => sessionStorage]) {
    try {
      areas.push(area());
    } catch {
      continue;
    }
  }
  return areas;
}

function removeDrafts(shouldRemove: (key: DraftKey, raw: string | null) => boolean): void {
  for (const storage of everyArea()) {
    try {
      for (let index = storage.length - 1; index >= 0; index--) {
        const storageKey = storage.key(index);
        const key = storageKey === null ? null : draftKeyOf(storageKey);
        if (storageKey !== null && key && shouldRemove(key, storage.getItem(storageKey))) {
          storage.removeItem(storageKey);
        }
      }
    } catch {
      continue;
    }
  }
}

function parse(raw: string | null): StoredDraft | null {
  if (!raw) return null;
  try {
    const stored: unknown = JSON.parse(raw);
    if (!isStoredDraft(stored)) return null;
    const age = Date.now() - stored.savedAt;
    return age >= 0 && age <= ACCEPTANCE_DRAFT_VALIDITY_DAYS * DAY_MS ? stored : null;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasText(record: Record<string, unknown>, fields: readonly string[]): boolean {
  return fields.every((field) => typeof record[field] === 'string');
}

function isStoredDraft(value: unknown): value is StoredDraft {
  if (!isRecord(value) || value['version'] !== DRAFT_VERSION) return false;
  if (typeof value['savedAt'] !== 'number' || !Number.isFinite(value['savedAt'])) return false;

  const terms = value['terms'];
  if (!isRecord(terms)) return false;

  const { billing, approval } = terms;
  if (!isRecord(billing) || !isRecord(approval)) return false;

  const emails = billing['invoiceEmails'];
  return (
    hasText(billing, BILLING_TEXT_FIELDS) &&
    hasText(approval, APPROVAL_TEXT_FIELDS) &&
    typeof approval['allowsResultsByEmail'] === 'boolean' &&
    Array.isArray(emails) &&
    emails.length >= INVOICE_EMAIL_COUNT.min &&
    emails.length <= INVOICE_EMAIL_COUNT.max &&
    emails.every((email) => typeof email === 'string')
  );
}
