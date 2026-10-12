import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Empresa, EmpresaRequest, EmpresaStatus } from '@/app/model/empresa';

@Injectable({ providedIn: 'root' })
export class EmpresasService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/companies`;

  list(search: string, status: EmpresaStatus | null): Observable<Empresa[]> {
    let params = new HttpParams();
    if (search.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    return this.http.get<Empresa[]>(this.endpoint, { params });
  }

  create(request: EmpresaRequest): Observable<Empresa> {
    return this.http.post<Empresa>(this.endpoint, request);
  }

  update(id: string, request: EmpresaRequest): Observable<Empresa> {
    return this.http.put<Empresa>(`${this.endpoint}/${encodeURIComponent(id)}`, request);
  }

  changeStatus(id: string, status: EmpresaStatus): Observable<Empresa> {
    return this.http.patch<Empresa>(`${this.endpoint}/${encodeURIComponent(id)}/status`, { status });
  }
}
