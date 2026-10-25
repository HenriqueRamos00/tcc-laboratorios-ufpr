import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Quote } from '@/app/model/quote';

@Injectable({ providedIn: 'root' })
export class QuotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/quotes`;

  getQuotes(): Observable<Quote[]> {
    return this.http.get<Quote[]>(this.baseUrl);
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
