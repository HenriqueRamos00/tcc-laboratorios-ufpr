import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { JwtHelperService } from '@auth0/angular-jwt';
import { catchError, finalize, map, Observable, of, shareReplay, tap } from 'rxjs';

import { mapClaimToRole, UserRole } from '@core/store/user-role.store';
import { API_URL } from '@/app/environment/env';
import { Login } from '@/app/model/login';
import { PortalUser, Token } from '@/app/model/token';

export const LS_TOKEN = 'lactec.token';
export const LS_USERNAME = 'lactec.username';
export const LS_USER = 'lactec.user';

export function tokenGetter(): string | null {
  return typeof localStorage === 'undefined'
    ? null
    : localStorage.getItem(LS_TOKEN) ?? sessionStorage.getItem(LS_TOKEN);
}

interface PortalClaims {
  authSource?: string;
  roles?: string;
  role?: string;
  serviceCode?: string;
  exp?: number;
}

function sessionStorageForToken(): Storage | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(LS_TOKEN) ? localStorage : sessionStorage;
}

function storedUser(): PortalUser | null {
  try {
    const user = tokenGetter() ? JSON.parse(sessionStorageForToken()?.getItem(LS_USER) ?? 'null') : null;
    return user && typeof user === 'object' ? user : null;
  } catch { return null; }
}

@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly http = inject(HttpClient);
  private readonly jwt = inject(JwtHelperService);
  private readonly userRole = inject(UserRole);
  private readonly baseUrl = `${API_URL}/auth`;
  private readonly identity = signal<PortalUser | null>(storedUser());
  readonly currentIdentity = this.identity.asReadonly();
  private readonly userName = signal<string | null>(
    tokenGetter() ? sessionStorageForToken()?.getItem(LS_USERNAME) ?? null : null,
  );
  readonly currentUser = this.userName.asReadonly();
  private restoredToken: string | null = null;
  private restoring: { token: string; request: Observable<boolean> } | null = null;

  login(credentials: Login, keepConnected = true): Observable<Token> {
    return this.http.post<Token>(`${this.baseUrl}/login`, {
      email: credentials.email.trim().toLowerCase(), password: credentials.password,
    }).pipe(tap(response => {
      const claims = this.clientClaims(response?.Token);
      if (!claims || typeof response.tokenType !== 'string' || response.tokenType.toLowerCase() !== 'bearer'
          || !Number.isFinite(response.expiresIn) || response.expiresIn <= 0
          || !this.validIdentity(response.user, claims)) {
        throw new Error('Resposta de autenticação inválida.');
      }

      this.logout();
      const storage = keepConnected ? localStorage : sessionStorage;
      storage.setItem(LS_TOKEN, response.Token);
      this.rememberUser(response.user);
      this.restoredToken = response.Token;
    }));
  }

  restoreSession(): Observable<boolean> {
    const token = tokenGetter();
    const claims = this.clientClaims(token);
    if (!token || !claims) {
      this.logout();
      return of(false);
    }
    if (claims.authSource !== 'lactec') {
      const user = storedUser();
      if (user && !this.validIdentity(user, claims)) {
        this.logout();
        return of(false);
      }
      this.identity.set(user);
      this.userName.set(user?.name ?? sessionStorageForToken()?.getItem(LS_USERNAME) ?? null);
      this.userRole.clear();
      this.userRole.setRoleFromTokenClaim(claims.role!, sessionStorageForToken() === localStorage);
      return of(true);
    }
    if (this.restoredToken === token && this.identity()) return of(true);
    if (this.restoring?.token === token) return this.restoring.request;
    const request = this.http.get<PortalUser>(`${this.baseUrl}/me`).pipe(
      map(user => {
        // Uma resposta antiga não deve restaurar a sessão após sair ou trocar de conta.
        if (tokenGetter() !== token) return false;
        if (!this.validIdentity(user, claims)) {
          this.logout();
          return false;
        }
        this.rememberUser(user);
        this.restoredToken = token;
        return true;
      }),
      catchError(error => {
        if ((error.status === 401 || error.status === 403) && tokenGetter() === token) this.logout();
        return of(false);
      }),
      finalize(() => { if (this.restoring?.token === token) this.restoring = null; }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.restoring = { token, request };
    return request;
  }

  logout(): void {
    if (typeof localStorage !== 'undefined') {
      for (const storage of [localStorage, sessionStorage]) {
        for (const key of [LS_TOKEN, LS_USERNAME, LS_USER]) storage.removeItem(key);
      }
    }
    this.identity.set(null);
    this.userName.set(null);
    this.restoredToken = null;
    this.restoring = null;
    this.userRole.clear();
  }

  private rememberUser(user: PortalUser): void {
    this.identity.set(user);
    this.userName.set(user.name);
    const storage = sessionStorageForToken();
    storage?.setItem(LS_USERNAME, user.name);
    storage?.setItem(LS_USER, JSON.stringify(user));
    this.userRole.clear();
    this.userRole.setRoleFromTokenClaim(user.role, storage === localStorage);
  }

  private clientClaims(token: unknown): PortalClaims | null {
    if (typeof token !== 'string' || token.split('.').length !== 3 || token.split('.').some(part => !part)) return null;
    try {
      const claims = this.jwt.decodeToken<PortalClaims>(token);
      if (!claims || typeof claims.exp !== 'number' || !Number.isFinite(claims.exp)
          || claims.exp * 1000 <= Date.now()
          || (claims.authSource !== undefined && claims.authSource !== 'lactec')) return null;

      const role = claims.authSource === 'lactec' ? claims.roles : claims.role;
      return typeof role === 'string' && mapClaimToRole(role)?.name === 'client' ? claims : null;
    } catch {
      return null;
    }
  }

  private validIdentity(user: PortalUser | null | undefined, claims: PortalClaims): user is PortalUser {
    if (!user || typeof user.id !== 'string' || !user.id.trim()
        || typeof user.name !== 'string' || !user.name.trim()
        || typeof user.email !== 'string' || !user.email.trim()
        || typeof user.role !== 'string' || mapClaimToRole(user.role)?.name !== 'client') return false;

    if (claims.authSource !== 'lactec') return true;
    return typeof user.serviceCode === 'string' && !!user.serviceCode.trim()
      && user.serviceCode === claims.serviceCode
      && Array.isArray(user.authorizations?.companyCnpjs)
      && user.authorizations.companyCnpjs.every(cnpj => typeof cnpj === 'string' && !!cnpj.trim());
  }
}
