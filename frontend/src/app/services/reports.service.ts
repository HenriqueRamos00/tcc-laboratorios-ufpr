import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type { AnalysisConclusion, PublishedReport } from '@/app/model/report';

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api`;

  /** GET /api/reports: todos os laudos publicados do cliente, de qualquer orçamento. */
  publishedReports(): Observable<readonly PublishedReport[]> {
    return this.http.get<readonly PublishedReport[]>(`${this.baseUrl}/reports`);
  }

  /**
   * GET /api/quotes/{id}/reports
   *
   * Devolve nulo quando o orçamento não tem laudo publicado: a tela precisa
   * distinguir "ainda não há laudo" de "a consulta falhou", e as duas coisas
   * dizem frases diferentes ao cliente.
   */
  analysisConclusion(quoteId: string): Observable<AnalysisConclusion | null> {
    return this.http.get<AnalysisConclusion | null>(
      `${this.baseUrl}/quotes/${encodeURIComponent(quoteId)}/reports`,
    );
  }

  /**
   * GET /api/reports/{id}/file
   *
   * Devolve os bytes do laudo, e não o endereço dele: o arquivo fica atrás do
   * token do portal, então quem baixa é o cliente HTTP, não a barra de
   * endereços do navegador.
   */
  downloadReport(reportId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reports/${encodeURIComponent(reportId)}/file`, {
      responseType: 'blob',
    });
  }
}
