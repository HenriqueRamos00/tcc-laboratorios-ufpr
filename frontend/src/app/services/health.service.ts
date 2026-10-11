import { Injectable } from '@angular/core';
import { delay, Observable, of, throwError } from 'rxjs';

import { DEMO_DATA_ENABLED } from '@/app/environment/env';

import type { IndicadoresDeSaude } from '@/app/model/health';

import { analiseSelecionadaDoHistorico } from './fixtures/equipamentos.fixture';
import { INDICADORES } from './fixtures/indicadores.fixture';

const LATENCIA_SIMULADA = 380;

@Injectable({ providedIn: 'root' })
export class HealthService {
  indicadoresDoEquipamento(
    equipamentoId: string,
    tag: string,
    relatorioId?: string,
  ): Observable<IndicadoresDeSaude> {
    if (!DEMO_DATA_ENABLED) {
      return throwError(() => new Error('Indicadores de demonstração estão desativados.'));
    }
    return of({
      ...INDICADORES,
      equipamentoId,
      tag,
      analiseSelecionada: analiseSelecionadaDoHistorico(equipamentoId, relatorioId),
    }).pipe(delay(LATENCIA_SIMULADA));
  }
}
