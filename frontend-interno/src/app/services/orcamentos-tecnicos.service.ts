import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import {
  OrcamentoTecnicoDetalhe,
  PaginaOrcamentosTecnicos,
  PesquisaOrcamentosTecnicos,
} from '@/app/model/orcamento-tecnico';

@Injectable({ providedIn: 'root' })
export class OrcamentosTecnicosService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/technician/quotes`;

  list(pesquisa: PesquisaOrcamentosTecnicos): Observable<PaginaOrcamentosTecnicos> {
    let params = new HttpParams()
      .set('page', pesquisa.page)
      .set('pageSize', pesquisa.pageSize);

    const search = pesquisa.search.trim();
    if (search) params = params.set('search', search);
    if (pesquisa.status) params = params.set('status', pesquisa.status);
    if (pesquisa.from) params = params.set('from', pesquisa.from);
    if (pesquisa.to) params = params.set('to', pesquisa.to);

    return this.http.get<PaginaOrcamentosTecnicos>(this.endpoint, { params });
  }

  get(id: string): Observable<OrcamentoTecnicoDetalhe> {
    return this.http.get<OrcamentoTecnicoDetalhe>(
      `${this.endpoint}/${encodeURIComponent(id)}`,
    );
  }
}
