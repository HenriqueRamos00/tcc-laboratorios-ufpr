import { Injectable } from '@angular/core';
import { delay, Observable, of, throwError } from 'rxjs';

import { DEMO_DATA_ENABLED } from '@/app/environment/env';

import type { ConclusaoDaAnalise } from '@/app/model/report';

import { CONCLUSAO, PDF_DE_EXEMPLO } from './fixtures/relatorios.fixture';

const LATENCIA_SIMULADA = 300;

@Injectable({ providedIn: 'root' })
export class ReportsService {
  conclusaoDaAnalise(_propostaId?: string): Observable<ConclusaoDaAnalise> {
    if (!DEMO_DATA_ENABLED) {
      return throwError(() => new Error('Relatórios de demonstração estão desativados.'));
    }
    return of(CONCLUSAO).pipe(delay(LATENCIA_SIMULADA));
  }

  baixarRelatorio(_relatorioId: string): Observable<Blob> {
    if (!DEMO_DATA_ENABLED) {
      return throwError(() => new Error('Relatórios de demonstração estão desativados.'));
    }
    return of(new Blob([PDF_DE_EXEMPLO], { type: 'application/pdf' })).pipe(
      delay(LATENCIA_SIMULADA),
    );
  }
}
