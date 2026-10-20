import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { AdminRole, AdminUser, AdminUserRequest } from '@/app/model/admin-user';

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/admin/users`;

  list(search: string, role: AdminRole | null, enabled: boolean | null): Observable<AdminUser[]> {
    let params = new HttpParams();
    if (search.trim()) params = params.set('search', search.trim());
    if (role) params = params.set('role', role);
    if (enabled !== null) params = params.set('enabled', enabled);
    return this.http.get<AdminUser[]>(this.endpoint, { params });
  }

  create(request: AdminUserRequest): Observable<AdminUser> {
    return this.http.post<AdminUser>(this.endpoint, request);
  }

  update(id: string, request: AdminUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.endpoint}/${encodeURIComponent(id)}`, request);
  }

  changeStatus(id: string, enabled: boolean): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.endpoint}/${encodeURIComponent(id)}/status`, {
      enabled,
    });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}
