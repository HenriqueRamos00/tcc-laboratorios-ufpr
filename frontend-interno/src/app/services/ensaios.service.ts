import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '@/app/environment/env';
import {
  InstrumentReference,
  MeasurementTemplate,
  Page,
  QuoteReference,
  SampleReference,
  AnalyzedEquipmentReference,
  TechnicianReference,
  TestDetail,
  TestRequest,
  TestStatusHistoryEntry,
  TestStatusUpdateRequest,
  TestStatusUpdateResponse,
  TestSummary,
  TestUpdateRequest,
} from '@/app/model/ensaio';
import {
  ReportPreparationResponse,
  TestReportsResponse,
} from '@/app/model/relatorio';

@Injectable({ providedIn: 'root' })
export class EnsaiosService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/tests`;
  list(search: string, page = 1, pageSize = 20): Observable<Page<TestSummary>> {
    let params = new HttpParams().set('page', page).set('pageSize', pageSize);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<TestSummary>>(this.endpoint, { params });
  }
  get(id: string): Observable<TestDetail> {
    return this.http.get<TestDetail>(
      `${this.endpoint}/${encodeURIComponent(id)}`,
    );
  }
  create(request: TestRequest): Observable<TestDetail> {
    return this.http.post<TestDetail>(this.endpoint, request);
  }
  update(id: string, request: TestUpdateRequest): Observable<TestDetail> {
    return this.http.put<TestDetail>(
      `${this.endpoint}/${encodeURIComponent(id)}`,
      request,
    );
  }
  updateStatus(
    id: string,
    request: TestStatusUpdateRequest,
  ): Observable<TestStatusUpdateResponse> {
    return this.http.patch<TestStatusUpdateResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/status`,
      request,
    );
  }
  statusHistory(id: string): Observable<TestStatusHistoryEntry[]> {
    return this.http.get<TestStatusHistoryEntry[]>(
      `${this.endpoint}/${encodeURIComponent(id)}/status-history`,
    );
  }
  reportsForTest(id: string): Observable<TestReportsResponse> {
    return this.http.get<TestReportsResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/reports`,
    );
  }
  prepareReport(id: string): Observable<ReportPreparationResponse> {
    return this.http.post<ReportPreparationResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/reports/preparation`,
      null,
    );
  }
  samples(
    quoteId: string | null,
    search = '',
    page = 1,
    pageSize = 20,
  ): Observable<Page<SampleReference>> {
    let params = new HttpParams().set('page', page).set('pageSize', pageSize);
    if (quoteId) params = params.set('quoteId', quoteId);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<SampleReference>>(
      `${this.endpoint}/references/samples`,
      { params },
    );
  }
  quotes(search = '', page = 1, pageSize = 20): Observable<Page<QuoteReference>> {
    let params = new HttpParams().set('page', page).set('pageSize', pageSize);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<QuoteReference>>(
      `${this.endpoint}/references/quotes`,
      { params },
    );
  }
  analyzedEquipment(
    quoteId: string,
    search = '',
    page = 1,
    pageSize = 20,
  ): Observable<Page<AnalyzedEquipmentReference>> {
    let params = new HttpParams()
      .set('quoteId', quoteId)
      .set('page', page)
      .set('pageSize', pageSize);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<AnalyzedEquipmentReference>>(
      `${this.endpoint}/references/analyzed-equipment`,
      { params },
    );
  }
  technicians(): Observable<TechnicianReference[]> {
    return this.http.get<TechnicianReference[]>(
      `${this.endpoint}/references/technicians`,
    );
  }
  instruments(): Observable<InstrumentReference[]> {
    return this.http.get<InstrumentReference[]>(
      `${this.endpoint}/references/instruments`,
    );
  }
  template(
    type: Exclude<NonNullable<TestDetail['analysisType']>, never>,
  ): Observable<{ analysisType: string; measurements: MeasurementTemplate[] }> {
    return this.http.get<{
      analysisType: string;
      measurements: MeasurementTemplate[];
    }>(`${this.endpoint}/measurement-templates/${type}`);
  }
}
