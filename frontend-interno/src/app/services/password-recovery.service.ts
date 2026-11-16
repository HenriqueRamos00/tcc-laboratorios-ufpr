import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';

@Injectable({ providedIn: 'root' })
export class PasswordRecoveryService {
  private readonly http = inject(HttpClient);

  request(email: string): Observable<void> {
    return this.http.post<void>(`${API_URL}/auth/password/forgot`, { email });
  }

  reset(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${API_URL}/auth/password/reset`, { token, password });
  }
}
