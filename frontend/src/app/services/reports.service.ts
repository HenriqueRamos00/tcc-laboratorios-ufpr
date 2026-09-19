import { Injectable } from '@angular/core';
import { delay, Observable, of } from 'rxjs';

import type { ConclusaoDaAnalise } from '@/app/model/report';

const LATENCIA_SIMULADA = 300;

const CONCLUSAO: ConclusaoDaAnalise = {
  protocolo: '#LCT-2024-9981-B',
  tipoDeEnsaio: 'Ensaio Químico de Materiais',
  resumo:
    'O processo de análise foi concluído com sucesso. Todos os parâmetros técnicos foram validados ' +
    'pela curadoria laboratorial e o laudo final está disponível para visualização.',
  relatorios: [
    {
      id: '1',
      codigo: 'LACTEC-00307/2026',
      protocolo: '00307/2026',
      amostra: 'Areia',
      laboratorio: 'Lab. de Óleos',
      publicadoEm: '07/03/2026',
    },
    {
      id: '2',
      codigo: 'LACTEC-00307/2026',
      protocolo: '00307/2026',
      amostra: 'Brita',
      laboratorio: 'Lab. de Óleos',
      publicadoEm: '07/03/2026',
    },
    {
      id: '3',
      codigo: '60101',
      protocolo: '00307/2026',
      amostra: 'Transformador',
      laboratorio: 'Lab. de Óleos',
      publicadoEm: '07/03/2026',
    },
    {
      id: '4',
      codigo: '61550',
      protocolo: '00307/2026',
      amostra: 'Transformador',
      laboratorio: 'Lab. de Óleos',
      publicadoEm: '07/03/2026',
    },
  ],
};

@Injectable({ providedIn: 'root' })
export class ReportsService {
  /** GET /api/quotes/{id}/reports */
  conclusaoDaAnalise(propostaId?: string): Observable<ConclusaoDaAnalise> {
    void propostaId;
    return of(CONCLUSAO).pipe(delay(LATENCIA_SIMULADA));
  }
}
