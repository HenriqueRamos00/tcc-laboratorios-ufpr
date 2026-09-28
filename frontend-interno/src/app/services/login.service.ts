import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { JwtHelperService } from '@auth0/angular-jwt';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';

import { UserRole, isInternalRole } from '@core/store/user-role.store';
import { API_URL } from '@/app/environment/env';
import { InternalLoginResponse, InternalUser } from '@/app/model/internal-user';
import { Login } from '@/app/model/login';

export const LS_TOKEN = 'lactec.internal.token';
export const LS_EXPIRES_AT = 'lactec.internal.expires-at';

function getStoredItem(key: string): string | null {
  if (typeof localStorage === 'undefined' || typeof sessionStorage === 'undefined') return null;
  return localStorage.getItem(key) ?? sessionStorage.getItem(key);
}

export function tokenGetter(): string | null {
  return getStoredItem(LS_TOKEN);
}

@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly http = inject(HttpClient);
  private readonly jwt = inject(JwtHelperService);
  private readonly userRole = inject(UserRole);
  private readonly router = inject(Router);

  private expirationTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly userName = signal<string | null>(this.userRole.user()?.name ?? null);
  readonly currentUser = this.userName.asReadonly();

  constructor() {
    this.scheduleExpiration();
  }

  login(credentials: Login, keepConnected: boolean): Observable<InternalLoginResponse> {
    return this.http
      .post<InternalLoginResponse>(API_URL + '/auth/internal/login', credentials)
      .pipe(
        map((response) => {
          if (
            !response?.accessToken ||
            !response.user ||
            !isInternalRole(response.user.role) ||
            !Number.isFinite(response.expiresIn) ||
            response.expiresIn <= 0
          ) {
            throw new Error('A resposta de autenticação da API está incompleta.');
          }
          return response;
        }),
        tap((response) => this.saveSession(response, keepConnected)),
      );
  }

  hasValidSession(): boolean {
    const token = tokenGetter();
    const expiresAt = Number(getStoredItem(LS_EXPIRES_AT));
    if (!token || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) return false;
    try {
      return !this.jwt.isTokenExpired(token);
    } catch {
      return false;
    }
  }

  logout(): void {
    if (this.expirationTimer) clearTimeout(this.expirationTimer);
    this.expirationTimer = null;
    if (typeof localStorage !== 'undefined' && typeof sessionStorage !== 'undefined') {
      for (const storage of [localStorage, sessionStorage]) {
        storage.removeItem(LS_TOKEN);
        storage.removeItem(LS_EXPIRES_AT);
      }
    }
    this.userName.set(null);
    this.userRole.clear();
  }

  private saveSession(response: InternalLoginResponse, keepConnected: boolean): void {
    const storage = keepConnected ? localStorage : sessionStorage;
    const otherStorage = keepConnected ? sessionStorage : localStorage;
    const expiresAt = Date.now() + response.expiresIn * 1000;

    for (const key of [LS_TOKEN, LS_EXPIRES_AT]) otherStorage.removeItem(key);
    storage.setItem(LS_TOKEN, response.accessToken);
    storage.setItem(LS_EXPIRES_AT, String(expiresAt));

    const user: InternalUser = {
      id: response.user.id,
      name: response.user.name,
      email: response.user.email,
      role: response.user.role,
    };
    this.userRole.setUser(user, keepConnected);
    this.userName.set(user.name);
    this.scheduleExpiration(expiresAt);
  }

  private scheduleExpiration(expiresAt = Number(getStoredItem(LS_EXPIRES_AT))): void {
    if (this.expirationTimer) clearTimeout(this.expirationTimer);
    this.expirationTimer = null;
    if (!Number.isFinite(expiresAt)) return;

    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      this.expireSession();
      return;
    }

    const maximumDelay = 2_147_000_000;
    this.expirationTimer = setTimeout(() => {
      if (remaining > maximumDelay) this.scheduleExpiration(expiresAt);
      else this.expireSession();
    }, Math.min(remaining, maximumDelay));
  }

  private expireSession(): void {
    this.logout();
    void this.router.navigateByUrl('/login');
  }
}
