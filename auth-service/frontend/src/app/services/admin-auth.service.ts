import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';

import { AdminSessionStore } from '@core/store/admin-session.store';
import { CsrfStore } from '@core/store/csrf.store';
import { API_URL } from '@/app/environment/env';
import { AdminSession, SetupRequest, SetupStatus } from '@/app/model/admin-user';
import { Login } from '@/app/model/login';

@Injectable({ providedIn: 'root' })
export class AdminAuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(AdminSessionStore);
  private readonly csrfStore = inject(CsrfStore);

  login(credentials: Login): Observable<AdminSession> {
    return this.http
      .post<AdminSession>(`${API_URL}/admin/auth/login`, credentials)
      .pipe(tap((session) => this.store.setUser(session)));
  }

  session(): Observable<AdminSession> {
    return this.http
      .get<AdminSession>(`${API_URL}/admin/auth/session`)
      .pipe(
        tap((session) => this.store.setUser(session)),
        switchMap((session) =>
          this.csrf().pipe(
            map(() => session),
            catchError(() => of(session)),
          ),
        ),
      );
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${API_URL}/admin/auth/logout`, {})
      .pipe(
        tap(() => {
          this.store.clear();
          this.csrfStore.clear();
        }),
      );
  }

  csrf(): Observable<{ token: string }> {
    return this.http
      .get<{ token: string }>(`${API_URL}/admin/auth/csrf`)
      .pipe(tap((response) => this.csrfStore.set(response.token)));
  }

  setupStatus(): Observable<SetupStatus> {
    return this.http.get<SetupStatus>(`${API_URL}/setup/status`);
  }

  setup(request: SetupRequest, token: string): Observable<void> {
    return this.http.post<void>(`${API_URL}/setup`, request, {
      headers: { 'X-Setup-Token': token },
    });
  }
}
