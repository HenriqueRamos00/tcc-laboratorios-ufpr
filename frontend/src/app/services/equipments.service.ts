import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type { Equipment, EquipmentDetail, EquipmentReport } from '@/app/model/equipment';
import { pageParams, toPage, type Page, type PageRequest } from '@/app/model/page';
import { toDownloadedFile, type DownloadedFile } from '@shared/file-download';

@Injectable({ providedIn: 'root' })
export class EquipmentsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/equipments`;

  /** GET /api/equipments */
  list(request: PageRequest): Observable<Page<Equipment>> {
    return this.http
      .get<Equipment[]>(this.baseUrl, { params: pageParams(request), observe: 'response' })
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
}
