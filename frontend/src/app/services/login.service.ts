import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { JwtHelperService } from '@auth0/angular-jwt';
import { catchError, finalize, map, Observable, of, shareReplay, tap } from 'rxjs';

import { UserRole } from '@core/store/user-role.store';
import { API_URL } from '@/app/environment/env';
import { Login } from '@/app/model/login';
import { PortalUser, Token } from '@/app/model/token';

export const LS_TOKEN = 'lactec.token';
export const LS_USERNAME = 'lactec.username';
export const LS_USER = 'lactec.user';

export function tokenGetter(): string | null {
  return typeof localStorage === 'undefined' ? null : localStorage.getItem(LS_TOKEN);
}

interface PortalClaims { authSource?: string; roles?: string; role?: string; nome?: string; exp?: number }

function storedUser(): PortalUser | null {
  try {
    const user: PortalUser | null = JSON.parse(localStorage.getItem(LS_USER) ?? 'null');
    return user && typeof user.name === 'string' && typeof user.role === 'string' ? user : null;
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
    typeof localStorage === 'undefined' ? null : localStorage.getItem(LS_USERNAME),
  );
  readonly currentUser = this.userName.asReadonly();
  private restoredToken: string | null = null;
  private restoring: { token: string; request: Observable<boolean> } | null = null;

  login(credentials: Login): Observable<Token> {
    return this.http.post<Token>(`${this.baseUrl}/login`, {
      email: credentials.email.trim().toLowerCase(), password: credentials.password,
    }).pipe(tap(response => {
      localStorage.setItem(LS_TOKEN, response.Token);
      this.rememberUser(response.user);
      this.restoredToken = response.Token;
    }));
  }

  restoreSession(): Observable<boolean> {
    const token = tokenGetter();
    try {
      if (!token || this.jwt.isTokenExpired(token)) {
        this.logout();
        return of(false);
      }
    } catch {
      this.logout();
      return of(false);
    }
    let claims: PortalClaims | null = null;
    try { claims = this.jwt.decodeToken<PortalClaims>(token); } catch {
      this.logout();
      return of(false);
    }
    if (!claims || typeof claims.exp !== 'number' || !Number.isFinite(claims.exp)
        || claims.exp * 1000 <= Date.now() || (claims.authSource && claims.authSource !== 'lactec')) {
      this.logout();
      return of(false);
    }
    if (claims.authSource !== 'lactec') {
      const role = claims.roles ?? claims.role;
      this.userRole.clear();
      if (typeof role !== 'string') {
        this.logout();
        return of(false);
      }
      this.userRole.setRoleFromTokenClaim(role);
      return of(this.userRole.role()?.name === 'client');
    }
    if (this.restoredToken === token && this.identity()) return of(true);
    if (this.restoring?.token === token) return this.restoring.request;
    const request = this.http.get<PortalUser>(`${this.baseUrl}/me`).pipe(
      map(user => {
        // An older response must not restore a session after logout or account switching.
        if (tokenGetter() !== token) return false;
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
    localStorage.removeItem(LS_TOKEN);
    localStorage.removeItem(LS_USERNAME);
    localStorage.removeItem(LS_USER);
    this.identity.set(null);
    this.userName.set(null);
    this.restoredToken = null;
    this.restoring = null;
    this.userRole.clear();
  }

  private rememberUser(user: PortalUser): void {
    this.identity.set(user);
    this.userName.set(user.name);
    localStorage.setItem(LS_USERNAME, user.name);
    localStorage.setItem(LS_USER, JSON.stringify(user));
    this.userRole.clear();
    this.userRole.setRoleFromTokenClaim(user.role);
  }
}
