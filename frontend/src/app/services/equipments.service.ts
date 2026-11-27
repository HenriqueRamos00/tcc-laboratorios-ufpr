import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, filter, map, type MonoTypeOperatorFunction, Observable, throwError } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type {
  Equipment,
  EquipmentDetail,
  EquipmentOverallStatus,
  EquipmentReport,
  EquipmentStatus,
  EquipmentStatusAccepted,
  EquipmentStatusRefresh,
  LoadFinishedNotice,
} from '@/app/model/equipment';
import { pageParams, toPage, type Page, type PageRequest } from '@/app/model/page';
import { toDownloadedFile, type DownloadedFile } from '@shared/file-download';

import { serverEvents } from './server-events';

export const EQUIPMENT_SEARCH_MAX_LENGTH = 100;

export const LOAD_FINISHED_EVENT = 'carga.concluida';

export const STATUS_BATCH_LIMIT = 100;

const STATUS_UNAVAILABLE_CODE = 'equipment-status-unavailable';

export interface EquipmentListRequest extends PageRequest {
  readonly search?: string;
  readonly status?: EquipmentOverallStatus | null;
}

export class EquipmentStatusUnavailable extends Error {
  constructor() {
    super(STATUS_UNAVAILABLE_CODE);
  }
}

@Injectable({ providedIn: 'root' })
export class EquipmentsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/equipments`;

  list(request: EquipmentListRequest): Observable<Page<Equipment>> {
    const search = request.search?.trim();
    const params: Record<string, string> = pageParams(request);
    if (search) params['search'] = search;
    if (request.status) params['status'] = request.status;
    return this.http
      .get<Equipment[]>(this.baseUrl, { params, observe: 'response' })
      .pipe(map((response) => toPage(response, request)));
  }

  /**
   * GET /api/equipments/{id}
   *
   * O identificador vem opaco da própria API e é usado como veio. Existe número de série
   * "111.594/1" e laudo endereçado pelo protocolo "01439/2026-004" no dado real, e a barra
   * dividiria o caminho da rota se o valor cru viajasse aqui.
   */
  findById(id: string): Observable<EquipmentDetail> {
    return this.http.get<EquipmentDetail>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  status(id: string): Observable<EquipmentStatus | null> {
    return this.http
      .get<EquipmentStatus | null>(`${this.baseUrl}/${encodeURIComponent(id)}/status`)
      .pipe(map((status) => status ?? null));
  }

  refreshStatus(id: string): Observable<EquipmentStatusRefresh> {
    return this.http
      .post<EquipmentStatusRefresh>(`${this.baseUrl}/${encodeURIComponent(id)}/status/refresh`, null)
      .pipe(unavailableAsItsOwnError());
  }

  refreshStatuses(ids: readonly string[]): Observable<EquipmentStatusAccepted> {
    return this.http
      .post<EquipmentStatusAccepted>(`${this.baseUrl}/status/refresh`, { ids })
      .pipe(unavailableAsItsOwnError());
  }

  refreshAllStatuses(): Observable<EquipmentStatusAccepted> {
    return this.http
      .post<EquipmentStatusAccepted>(`${this.baseUrl}/status/sync`, null)
      .pipe(unavailableAsItsOwnError());
  }

  loadFinishedNotices(): Observable<LoadFinishedNotice> {
    return serverEvents(this.http, `${API_URL}/api/events`).pipe(
      filter((event) => event.name === LOAD_FINISHED_EVENT),
      map((event) => JSON.parse(event.data) as LoadFinishedNotice),
    );
  }

  /** GET /api/equipments/{id}/reports */
  reportHistory(id: string, request: PageRequest): Observable<Page<EquipmentReport>> {
    return this.http
      .get<EquipmentReport[]>(`${this.baseUrl}/${encodeURIComponent(id)}/reports`, {
        params: pageParams(request),
        observe: 'response',
      })
      .pipe(map((response) => toPage(response, request)));
  }

  reportDocument(id: string, reportId: string): Observable<DownloadedFile> {
    return this.http
      .get(
        `${this.baseUrl}/${encodeURIComponent(id)}/reports/${encodeURIComponent(reportId)}/document`,
        { observe: 'response', responseType: 'blob' },
      )
      .pipe(map(toDownloadedFile));
  }

  reportArchive(id: string): Observable<DownloadedFile | null> {
    return this.http
      .get(`${this.baseUrl}/${encodeURIComponent(id)}/reports/archive`, {
        observe: 'response',
        responseType: 'blob',
      })
      .pipe(map((response) => (response.status === 204 ? null : toDownloadedFile(response))));
  }
}

function unavailableAsItsOwnError<T>(): MonoTypeOperatorFunction<T> {
  return catchError((error: unknown) =>
    throwError(() =>
      error instanceof HttpErrorResponse
      && error.status === 404
      && error.error?.code === STATUS_UNAVAILABLE_CODE
        ? new EquipmentStatusUnavailable()
        : error,
    ),
  );
}
