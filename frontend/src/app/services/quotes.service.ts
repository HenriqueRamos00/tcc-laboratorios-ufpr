import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, throwError } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Quote } from '@/app/model/quote';

import { DEMONSTRATION_QUOTES } from './fixtures/quotes.fixture';

// MODO DEMONSTRAÇÃO: quando a API não responde, o serviço cai na massa de
// `quotes.fixture.ts` em vez de deixar a tela vazia, para que o portal
// possa ser apresentado sem o backend no ar. Para exigir a API de verdade,
// troque a constante abaixo para `false`: os erros voltam a subir e a tela
// mostra "Não foi possível carregar os orçamentos no momento".
const CAI_PARA_DEMONSTRACAO_SEM_API = true;

@Injectable({ providedIn: 'root' })
export class QuotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/quotes`;

  getQuotes(): Observable<Quote[]> {
    return this.http.get<unknown>(this.baseUrl).pipe(
      catchError((erro) => {
        if (!CAI_PARA_DEMONSTRACAO_SEM_API) return throwError(() => erro);
        console.warn('[portal] API indisponível; exibindo a massa de demonstração.', erro);
        return of([...DEMONSTRATION_QUOTES]);
      }),
      map((response) => {
        if (!Array.isArray(response)) throw new Error('A resposta de orçamentos precisa ser uma lista.');
        return response as Quote[];
      }),
    );
  }

  getQuoteById(id: string): Observable<Quote> {
    return this.http.get<Quote>(`${this.baseUrl}/${encodeURIComponent(id)}`).pipe(
      catchError((erro) => {
        const daDemonstracao = DEMONSTRATION_QUOTES.find((orcamento) => orcamento.id === id);
        if (!CAI_PARA_DEMONSTRACAO_SEM_API || !daDemonstracao) return throwError(() => erro);
        console.warn('[portal] API indisponível; exibindo a massa de demonstração.', erro);
        return of(daDemonstracao);
      }),
    );
  }

  getQuoteDocument(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${encodeURIComponent(id)}/document`, {
      responseType: 'blob',
    });
  }
}
