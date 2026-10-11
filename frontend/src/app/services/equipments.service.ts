import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type { Equipment, EquipmentDetail, EquipmentReport } from '@/app/model/equipment';

@Injectable({ providedIn: 'root' })
export class EquipmentsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/equipments`;

  /** GET /api/equipments */
  list(): Observable<readonly Equipment[]> {
    return this.http.get<Equipment[]>(this.baseUrl);
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
  reportHistory(id: string): Observable<readonly EquipmentReport[]> {
    return this.http.get<EquipmentReport[]>(`${this.baseUrl}/${encodeURIComponent(id)}/reports`);
  }
}
