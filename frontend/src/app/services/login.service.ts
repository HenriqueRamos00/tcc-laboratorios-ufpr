import { HttpClient, HttpHeaders } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { map, Observable, of, tap } from 'rxjs';

import { CLIENT_ROLE } from '@/app/model/roles';
import { Login } from '@/app/model/login';
import { AuthenticatedUser, Token } from '@/app/model/token';
import { AUTH_API_URL } from '@/app/environment/env';
import { UserRole } from '@core/store/user-role.store';

import { tryDevLogin } from './dev-login';

export const LS_SESSION = 'lactec.client.session.v2';
const LEGACY_STORAGE_KEYS = ['lactec.token', 'lactec.username', 'lactec.role'];

interface StoredSession extends Token {
  expiresAt: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isAuthenticatedUser(value: unknown): value is AuthenticatedUser {
  if (!isRecord(value)) return false;
  return (
    typeof value['id'] === 'string' &&
    typeof value['name'] === 'string' &&
    typeof value['email'] === 'string' &&
    value['role'] === 'Client' &&
    Array.isArray(value['companies']) &&
    value['companies'].every(
      (company) =>
        isRecord(company) &&
        typeof company['id'] === 'string' &&
        typeof company['name'] === 'string',
    )
  );
}

export function isTokenResponse(value: unknown): value is Token {
  if (!isRecord(value)) return false;
  return (
    typeof value['accessToken'] === 'string' &&
    value['accessToken'].trim().length > 0 &&
    typeof value['tokenType'] === 'string' &&
    value['tokenType'].toLowerCase() === 'bearer' &&
    typeof value['expiresIn'] === 'number' &&
    Number.isFinite(value['expiresIn']) &&
    value['expiresIn'] > 0 &&
    isAuthenticatedUser(value['user'])
  );
}

function browserStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.localStorage;
}

function validStoredSession(raw: string | null): StoredSession | null {
  if (!raw) return null;
  try {
    const value: unknown = JSON.parse(raw);
    if (!isRecord(value) || !isTokenResponse(value) || typeof value['expiresAt'] !== 'number') {
      return null;
    }
    if (!Number.isFinite(value['expiresAt']) || value['expiresAt'] <= Date.now()) return null;
    return {
      ...value,
      expiresAt: value['expiresAt'],
    };
  } catch {
    return null;
  }
}

function readStoredSession(): StoredSession | null {
  if (typeof window === 'undefined') return null;
  const raw = window.localStorage.getItem(LS_SESSION) ?? window.sessionStorage.getItem(LS_SESSION);
  return validStoredSession(raw);
}

@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly http = inject(HttpClient);
  private readonly userRole = inject(UserRole);
  private readonly session = signal<StoredSession | null>(null);

  private readonly baseUrl = `${AUTH_API_URL}/auth`;
  private readonly httpOptions = {
    headers: new HttpHeaders({ 'Content-Type': 'application/json' }),
  };

  readonly currentProfile = computed(() => this.session()?.user ?? null);
  readonly currentUser = computed(() => this.currentProfile()?.name ?? null);

  constructor() {
    this.clearLegacyStorage();
    const stored = readStoredSession();
    if (stored) {
      this.session.set(stored);
      this.userRole.setRole(CLIENT_ROLE);
    } else {
      this.clearSessionStorage();
    }
  }

  login(credentials: Login, keepConnected = true): Observable<Token> {
    const demo = tryDevLogin(credentials.email, credentials.password);
    const response = demo
      ? of(demo)
      : this.http.post<unknown>(`${this.baseUrl}/login`, credentials, this.httpOptions);
    return response.pipe(
      map((response) => {
        if (!isTokenResponse(response)) throw new Error('Resposta de autenticação incompatível.');
        return response;
      }),
      tap((response) => this.saveSession(response, keepConnected)),
    );
  }

  hasValidSession(): boolean {
    const current = this.session() ?? readStoredSession();
    if (!current || current.expiresAt <= Date.now() || current.user.role !== 'Client') {
      this.logout();
      return false;
    }
    if (!this.session()) {
      this.session.set(current);
      this.userRole.setRole(CLIENT_ROLE);
    }
    return true;
  }

  authorizationHeader(): string | null {
    if (!this.hasValidSession()) return null;
    const current = this.session();
    return current ? `${current.tokenType} ${current.accessToken}` : null;
  }

  logout(): void {
    this.session.set(null);
    this.clearSessionStorage();
    this.userRole.clear();
  }

  private saveSession(response: Token, keepConnected: boolean): void {
    const stored: StoredSession = {
      ...response,
      user: {
        ...response.user,
        companies: response.user.companies.map((company) => ({ ...company })),
      },
      expiresAt: Date.now() + response.expiresIn * 1000,
    };
    this.clearSessionStorage();
    const storage = browserStorage();
    if (storage) {
      (keepConnected ? storage : window.sessionStorage).setItem(LS_SESSION, JSON.stringify(stored));
    }
    this.session.set(stored);
    this.userRole.setRole(CLIENT_ROLE);
  }

  private clearLegacyStorage(): void {
    if (typeof window === 'undefined') return;
    for (const key of LEGACY_STORAGE_KEYS) {
      window.localStorage.removeItem(key);
      window.sessionStorage.removeItem(key);
    }
  }

  private clearSessionStorage(): void {
    if (typeof window === 'undefined') return;
    window.localStorage.removeItem(LS_SESSION);
    window.sessionStorage.removeItem(LS_SESSION);
  }
}
