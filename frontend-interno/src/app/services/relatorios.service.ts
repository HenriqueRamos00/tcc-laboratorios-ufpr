import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Page } from '@/app/model/ensaio';
import {
  ReportDetail,
  ReportPublicationResponse,
  ReportSummary,
  ReportValidationResponse,
} from '@/app/model/relatorio';

export interface ReportSearch {
  readonly search: string;
  readonly validationStatus: ReportSummary['validationStatus'] | null;
  readonly published: boolean | null;
  readonly page: number;
  readonly pageSize: number;
}

@Injectable({ providedIn: 'root' })
export class RelatoriosService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/reports`;

  list(request: ReportSearch): Observable<Page<ReportSummary>> {
    let params = new HttpParams()
      .set('page', request.page)
      .set('pageSize', request.pageSize);
    const search = request.search.trim();

    if (search) params = params.set('search', search);
    if (request.validationStatus) {
      params = params.set('validationStatus', request.validationStatus);
    }
    if (request.published !== null) {
      params = params.set('published', request.published);
    }

    return this.http.get<Page<ReportSummary>>(this.endpoint, { params });
  }

  get(id: string): Observable<ReportDetail> {
    return this.http.get<ReportDetail>(
      `${this.endpoint}/${encodeURIComponent(id)}`,
    );
  }

  validate(
    id: string,
    version: number,
    contentFingerprint: string,
  ): Observable<ReportValidationResponse> {
    return this.http.post<ReportValidationResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/validation`,
      { version, contentFingerprint },
    );
  }

  publish(
    id: string,
    version: number,
  ): Observable<ReportPublicationResponse> {
    return this.http.post<ReportPublicationResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/publication`,
      { version },
    );
  }

  pdf(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.endpoint}/${encodeURIComponent(id)}/pdf`, {
      observe: 'response',
      responseType: 'blob',
    });
  }
}
