import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type { HealthIndicators } from '@/app/model/health';

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_URL}/api/equipments`;

  /**
   * GET /api/equipments/{id}/health para a visão geral e
   * GET /api/equipments/{id}/health/reports/{reportId} para uma análise.
   *
   * A TAG não é parâmetro: ela vem no próprio corpo, porque é a API que sabe se o
   * equipamento tem número de série ou se a tela precisa mostrar a identificação do laudo
   * no lugar.
   */
  equipmentIndicators(equipmentId: string, reportId?: string): Observable<HealthIndicators> {
    const equipment = encodeURIComponent(equipmentId);
    const address = reportId
      ? `${this.baseUrl}/${equipment}/health/reports/${encodeURIComponent(reportId)}`
      : `${this.baseUrl}/${equipment}/health`;

    return this.http.get<HealthIndicators>(address);
  }
}
