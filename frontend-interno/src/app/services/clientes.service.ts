import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import {
  Cliente,
  ClienteCreateRequest,
  ClienteStatus,
  ClienteUpdateRequest,
} from '@/app/model/cliente';

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/clients`;

  list(search: string, status: ClienteStatus | null): Observable<Cliente[]> {
    let params = new HttpParams();
    if (search.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    return this.http.get<Cliente[]>(this.endpoint, { params });
  }

  create(request: ClienteCreateRequest): Observable<Cliente> {
    return this.http.post<Cliente>(this.endpoint, request);
  }

  update(id: string, request: ClienteUpdateRequest): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.endpoint}/${encodeURIComponent(id)}`, request);
  }

  changeStatus(id: string, status: ClienteStatus): Observable<Cliente> {
    return this.http.patch<Cliente>(`${this.endpoint}/${encodeURIComponent(id)}/status`, { status });
  }
}
