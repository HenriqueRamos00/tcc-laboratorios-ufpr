import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { JwtHelperService } from '@auth0/angular-jwt';
import { Observable, of, tap } from 'rxjs';

import { UserRole } from '@core/store/user-role.store';

import { API_URL } from '@/app/environment/env';
import { Login } from '@/app/model/login';
import { Token } from '@/app/model/token';

import { tryDevLogin } from './dev-login';

export const LS_TOKEN = 'lactec.token';
export const LS_USERNAME = 'lactec.username';

export function tokenGetter(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(LS_TOKEN);
}

@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly http = inject(HttpClient);
  private readonly jwt = inject(JwtHelperService);
  private readonly userRole = inject(UserRole);

  private readonly baseUrl = `${API_URL}/auth`;
  private readonly httpOptions = {
    headers: new HttpHeaders({ 'Content-Type': 'application/json' }),
  };

  private readonly userName = signal<string | null>(
    typeof localStorage !== 'undefined' ? localStorage.getItem(LS_USERNAME) : null,
  );
  readonly currentUser = this.userName.asReadonly();

  login(credentials: Login): Observable<Token> {
    // DEV-ONLY: remover este bloco e o import de tryDevLogin/of quando o backend estiver pronto.
    const dev = tryDevLogin(credentials.email, credentials.password);
    if (dev) {
      localStorage.setItem(LS_TOKEN, dev.token.Token);
      localStorage.setItem(LS_USERNAME, dev.username);
      this.userName.set(dev.username);
      this.userRole.setRoleFromTokenClaim(dev.roleClaim);
      return of(dev.token);
    }

    return this.http.post<Token>(`${this.baseUrl}/login`, credentials, this.httpOptions).pipe(
      tap((token) => {
        localStorage.setItem(LS_TOKEN, token.Token);
        const decoded = this.jwt.decodeToken(token.Token) as {
          roles?: string;
          nome?: string;
        } | null;
        if (decoded?.nome) {
          localStorage.setItem(LS_USERNAME, decoded.nome);
          this.userName.set(decoded.nome);
        }
        if (decoded?.roles) {
          this.userRole.setRoleFromTokenClaim(decoded.roles);
        }
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(LS_TOKEN);
    localStorage.removeItem(LS_USERNAME);
    this.userName.set(null);
    this.userRole.clear();
  }
}
