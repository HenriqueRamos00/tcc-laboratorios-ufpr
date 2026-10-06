import { Injectable } from '@angular/core';
import { delay, Observable, of } from 'rxjs';

import type { IndicadoresDeSaude } from '@/app/model/health';

import { analiseSelecionadaDoHistorico } from './fixtures/equipamentos.fixture';
import { INDICADORES } from './fixtures/indicadores.fixture';

const LATENCIA_SIMULADA = 380;

@Injectable({ providedIn: 'root' })
export class HealthService {
  /**
   * GET /api/equipments/{id}/health para a visão geral e
   * GET /api/equipments/{id}/health/reports/{relatorioId} para uma análise.
   */
  indicadoresDoEquipamento(
    equipamentoId: string,
    tag: string,
    relatorioId?: string,
  ): Observable<IndicadoresDeSaude> {
    return of({
      ...INDICADORES,
      equipamentoId,
      tag,
      analiseSelecionada: analiseSelecionadaDoHistorico(equipamentoId, relatorioId),
    }).pipe(delay(LATENCIA_SIMULADA));
  }
}
