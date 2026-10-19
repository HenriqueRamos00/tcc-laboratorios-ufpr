import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import {
  OrcamentoDetalhe,
  OrcamentoRequest,
  PaginaOrcamentos,
  PesquisaOrcamentos,
} from '@/app/model/orcamento';

@Injectable({ providedIn: 'root' })
export class OrcamentosService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/quotes`;

  list(pesquisa: PesquisaOrcamentos): Observable<PaginaOrcamentos> {
    let params = new HttpParams()
      .set('page', pesquisa.page)
      .set('pageSize', pesquisa.pageSize);
    const search = pesquisa.search.trim();
    if (search) params = params.set('search', search);
    if (pesquisa.status) params = params.set('status', pesquisa.status);
    return this.http.get<PaginaOrcamentos>(this.endpoint, { params });
  }

  get(id: string): Observable<OrcamentoDetalhe> {
    return this.http.get<OrcamentoDetalhe>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  create(request: OrcamentoRequest, document?: File): Observable<OrcamentoDetalhe> {
    const body = this.createBody(request, document);
    return this.http.post<OrcamentoDetalhe>(this.endpoint, body);
  }

  update(id: string, request: OrcamentoRequest, document?: File): Observable<OrcamentoDetalhe> {
    const body = this.createBody(request, document);
    return this.http.put<OrcamentoDetalhe>(`${this.endpoint}/${encodeURIComponent(id)}`, body);
  }

  downloadDocument(id: string): Observable<Blob> {
    return this.http.get(`${this.endpoint}/${encodeURIComponent(id)}/document`, {
      responseType: 'blob',
    });
  }

  private createBody(request: OrcamentoRequest, document?: File): OrcamentoRequest | FormData {
    if (!document) return request;

    const body = new FormData();
    body.append('quote', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    body.append('document', document.slice(0, document.size, 'application/pdf'), document.name);
    return body;
  }
}
