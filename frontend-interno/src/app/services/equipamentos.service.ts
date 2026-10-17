import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Equipamento, EquipamentoRequest, EquipamentoRisco, EquipamentoTipo } from '@/app/model/equipamento';
import { Empresa } from '@/app/model/empresa';

export type EmpresaEquipamento = Pick<Empresa, 'id' | 'legalName' | 'cnpj' | 'status'>;

@Injectable({ providedIn: 'root' })
export class EquipamentosService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/equipments`;

  listCompanies(): Observable<EmpresaEquipamento[]> {
    return this.http.get<EmpresaEquipamento[]>(`${this.endpoint}/companies`);
  }

  list(search: string, type: EquipamentoTipo | null, risk: EquipamentoRisco | null): Observable<Equipamento[]> {
    let params = new HttpParams();
    if (search.trim()) params = params.set('search', search.trim());
    if (type) params = params.set('type', type);
    if (risk) params = params.set('risk', risk);
    return this.http.get<Equipamento[]>(this.endpoint, { params });
  }

  create(request: EquipamentoRequest): Observable<Equipamento> {
    return this.http.post<Equipamento>(this.endpoint, request);
  }

  update(id: number, request: EquipamentoRequest): Observable<Equipamento> {
    return this.http.put<Equipamento>(`${this.endpoint}/${id}`, request);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }
}
