import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { pageParams, toPage, type Page, type PageRequest } from '@/app/model/page';
import { Quote } from '@/app/model/quote';

@Injectable({ providedIn: 'root' })
export class QuotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/quotes`;

  /**
   * GET /api/quotes?page=&pageSize=
   *
   * A lista é paginada na origem (o Salesforce aplica LIMIT/OFFSET), como as
   * demais listas do portal: o corpo é o array da página e o total vem em
   * `X-Total-Count`. Pedir sem página traria só a primeira, e o cliente com
   * mais orçamentos do que isso deixaria de alcançar os outros.
   */
  list(request: PageRequest): Observable<Page<Quote>> {
    return this.http
      .get<Quote[]>(this.baseUrl, { params: pageParams(request), observe: 'response' })
      .pipe(map((response) => toPage(response, request)));
  }

  /**
   * GET /api/quotes/{id}/document
   *
   * Devolve os bytes da proposta, e não o endereço dela: o arquivo fica atrás
   * do token do portal, então quem baixa é o cliente HTTP, não a barra de
   * endereços do navegador. Aqui não há massa de demonstração: proposta sem
   * PDF publicado tem que falhar, para a tela dizer isso ao cliente em vez de
   * salvar um arquivo que não é o orçamento dele.
   */
  quoteDocument(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${encodeURIComponent(id)}/document`, {
      responseType: 'blob',
    });
  }

  getQuoteById(id: string): Observable<Quote> {
    return this.http.get<Quote>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
}
