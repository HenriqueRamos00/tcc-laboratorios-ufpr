import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Quote } from '@/app/model/quote';

@Injectable({ providedIn: 'root' })
export class QuotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/client/quotes`;

  getQuotes(): Observable<Quote[]> {
    return this.http.get<unknown>(this.baseUrl).pipe(
      map((response) => {
        if (!Array.isArray(response)) throw new Error('A resposta de orçamentos precisa ser uma lista.');
        return response as Quote[];
      }),
    );
  }

  getQuoteById(id: string): Observable<Quote> {
    return this.http.get<Quote>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  getQuoteDocument(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${encodeURIComponent(id)}/document`, {
      responseType: 'blob',
    });
  }
}
