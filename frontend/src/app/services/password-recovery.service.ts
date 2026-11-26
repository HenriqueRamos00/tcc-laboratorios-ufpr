import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';

@Injectable({ providedIn: 'root' })
export class PasswordRecoveryService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/auth`;

  request(email: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/recoveries`, { email });
  }

  reset(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/password-resets`, { token, password });
  }

  confirmEmail(token: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/confirmations`, { token });
  }
}
