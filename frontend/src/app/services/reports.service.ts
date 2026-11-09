import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { pageParams, toPage, type Page, type PageRequest } from '@/app/model/page';
import type { Report } from '@/app/model/report';
import { toDownloadedFile, type DownloadedFile } from '@shared/file-download';

export interface ReportQuery extends PageRequest {
  readonly quoteCode?: string;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/reports`;

  list(query: ReportQuery): Observable<Page<Report>> {
    const params: Record<string, string> = pageParams(query);
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
}
