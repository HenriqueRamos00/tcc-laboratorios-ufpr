import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { pageParams, toPage, type Page, type PageRequest } from '@/app/model/page';
import type { AnalysisConclusion, PublishedReport, Report } from '@/app/model/report';
import { toDownloadedFile, type DownloadedFile } from '@shared/file-download';

export interface ReportQuery extends PageRequest {
  readonly quoteCode?: string;
  readonly quoteId?: string;
}

export interface ReportPage extends Page<Report> {
  readonly testType?: string | null;
  readonly summary?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/reports`;

  list(query: ReportQuery): Observable<ReportPage> {
    const params: Record<string, string> = pageParams(query);
    if (query.quoteId) {
      return this.http
        .get<{ reports: Report[]; testType: string; summary: string }>(
          `${API_URL}/api/quotes/${encodeURIComponent(query.quoteId)}/reports`,
          { params, observe: 'response' },
        )
        .pipe(map((response) => ({
          ...toPage(response.clone({ body: response.body?.reports ?? [] }), query),
          testType: response.body?.testType || null,
          summary: response.body?.summary || null,
        })));
    }
    if (query.quoteCode) params['quoteCode'] = query.quoteCode;
    return this.http
      .get<Report[]>(this.baseUrl, { params, observe: 'response' })
      .pipe(map((response) => toPage(response, query)));
  }

  signedDocument(id: string): Observable<DownloadedFile> {
    return this.http
      .get(`${this.baseUrl}/${encodeURIComponent(id)}/document`, {
        observe: 'response',
        responseType: 'blob',
      })
      .pipe(map(toDownloadedFile));
  }

  publishedReports(): Observable<readonly PublishedReport[]> {
    return this.http.get<readonly PublishedReport[]>(this.baseUrl);
  }

  analysisConclusion(quoteId: string): Observable<AnalysisConclusion | null> {
    return this.http.get<AnalysisConclusion | null>(
      `${API_URL}/api/quotes/${encodeURIComponent(quoteId)}/reports`,
    );
  }

  downloadReport(reportId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${encodeURIComponent(reportId)}/file`, { responseType: 'blob' });
  }
}
