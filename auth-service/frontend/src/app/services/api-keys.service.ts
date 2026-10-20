import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { ApiKey, ApiKeyCreateRequest, ApiKeyCreateResponse } from '@/app/model/api-key';

@Injectable({ providedIn: 'root' })
export class ApiKeysService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/admin/api-keys`;

  list(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(this.endpoint);
  }

  create(request: ApiKeyCreateRequest): Observable<ApiKeyCreateResponse> {
    return this.http.post<ApiKeyCreateResponse>(this.endpoint, request);
  }

  changeStatus(id: string, enabled: boolean): Observable<ApiKey> {
    return this.http.patch<ApiKey>(`${this.endpoint}/${encodeURIComponent(id)}/status`, {
      enabled,
    });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}
