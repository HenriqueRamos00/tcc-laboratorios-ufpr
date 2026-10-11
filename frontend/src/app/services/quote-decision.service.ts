import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';

import type { RecusaDeOrcamento, TermoDeAceite } from '@/app/model/acceptance';

const INDISPONIVEL = 'Aceite e recusa ainda não estão conectados à API.';

@Injectable({ providedIn: 'root' })
export class QuoteDecisionService {
  aceitar(_termo: TermoDeAceite): Observable<never> {
    return throwError(() => new Error(INDISPONIVEL));
  }

  aceitarComOrdemDeCompra(_propostaId: string, _arquivo: File): Observable<never> {
    return throwError(() => new Error(INDISPONIVEL));
  }

  recusar(_recusa: RecusaDeOrcamento): Observable<never> {
    return throwError(() => new Error(INDISPONIVEL));
  }
}
