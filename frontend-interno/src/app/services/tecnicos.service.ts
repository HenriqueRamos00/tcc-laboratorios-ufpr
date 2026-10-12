import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import {
  CreateTecnicoRequest,
  Tecnico,
  TecnicoStatus,
  UpdateTecnicoRequest,
} from '@/app/model/tecnico';

@Injectable({ providedIn: 'root' })
export class TecnicosService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/technicians`;

  list(search: string, status: TecnicoStatus | null): Observable<Tecnico[]> {
    let params = new HttpParams();
    if (search.trim()) params = params.set('search', search.trim());
    if (status) params = params.set('status', status);
    return this.http.get<Tecnico[]>(this.endpoint, { params });
  }

  create(request: CreateTecnicoRequest): Observable<Tecnico> {
    return this.http.post<Tecnico>(this.endpoint, request);
  }

  update(id: number, request: UpdateTecnicoRequest): Observable<Tecnico> {
    return this.http.put<Tecnico>(`${this.endpoint}/${id}`, request);
  }

  changeStatus(id: number, status: TecnicoStatus): Observable<Tecnico> {
    return this.http.patch<Tecnico>(`${this.endpoint}/${id}/status`, { status });
  }
}
