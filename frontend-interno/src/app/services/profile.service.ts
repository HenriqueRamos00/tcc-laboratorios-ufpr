import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import {
  EmailChangeConfirmationResponse,
  PendingEmailChange,
  Profile,
  ProfileUpdateRequest,
} from '@/app/model/profile';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/auth/internal/profile`;

  getProfile(): Observable<Profile> {
    return this.http.get<Profile>(this.baseUrl);
  }

  updatePersonalData(request: ProfileUpdateRequest): Observable<Profile> {
    return this.http.patch<Profile>(this.baseUrl, request);
  }

  requestEmailChange(newEmail: string, currentPassword: string): Observable<PendingEmailChange> {
    return this.http.post<PendingEmailChange>(`${this.baseUrl}/email-change`, {
      newEmail,
      currentPassword,
    });
  }

  cancelEmailChange(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/email-change`);
  }

  confirmEmailChange(token: string): Observable<EmailChangeConfirmationResponse> {
    return this.http.post<EmailChangeConfirmationResponse>(
      `${this.baseUrl}/email-change/confirm`,
      { token },
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/password`, { currentPassword, newPassword });
  }
}
