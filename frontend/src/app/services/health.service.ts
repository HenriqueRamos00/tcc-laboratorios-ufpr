import { Injectable } from '@angular/core';
import { delay, Observable, of } from 'rxjs';

import type { IndicadoresDeSaude } from '@/app/model/health';

import { INDICADORES } from './fixtures/indicadores.fixture';

const LATENCIA_SIMULADA = 380;

@Injectable({ providedIn: 'root' })
export class HealthService {
  /** GET /api/equipments/{id}/health */
  indicadoresDoEquipamento(id: string, tag: string): Observable<IndicadoresDeSaude> {
    return of({ ...INDICADORES, equipamentoId: id, tag }).pipe(delay(LATENCIA_SIMULADA));
  }
}
