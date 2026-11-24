import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '@/app/environment/env';
import { LoginRequest, LoginResponse } from '@/app/model/login';

export const loginUrl = `${environment.apiUrl}/admin/auth/login`;

@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<(LoginResponse & { expiresAt: number }) | null>(null);
  private expirationTimer?: ReturnType<typeof setTimeout>;

  readonly currentUser = computed(() => this.session()?.user ?? null);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(loginUrl, credentials).pipe(
      tap((response) => {
        const expiresAt = Date.now() + response?.expiresIn * 1000;
        if (
          response?.tokenType !== 'Bearer' ||
          typeof response.accessToken !== 'string' ||
          !/^\S+$/.test(response.accessToken) ||
          !Number.isSafeInteger(response.expiresIn) ||
          !Number.isSafeInteger(expiresAt) ||
          response.expiresIn <= 0 ||
          typeof response.user?.id !== 'string' ||
          typeof response.user.name !== 'string' ||
          typeof response.user.email !== 'string'
        ) {
          throw new Error('Resposta de autenticação inválida.');
        }

        clearTimeout(this.expirationTimer);
        this.session.set({ ...response, expiresAt });
        this.scheduleExpiration(expiresAt);
      }),
    );
  }

  accessToken(): string | null {
    const session = this.session();
    return session && session.expiresAt > Date.now() ? session.accessToken : null;
  }

  signOut(): void {
    clearTimeout(this.expirationTimer);
    this.session.set(null);
    void this.router.navigate(['/login'], { replaceUrl: true });
  }

  private scheduleExpiration(expiresAt: number): void {
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      this.signOut();
      return;
    }

    this.expirationTimer = setTimeout(
      () => this.scheduleExpiration(expiresAt),
      Math.min(remaining, 2_147_483_647),
    );
  }
}
