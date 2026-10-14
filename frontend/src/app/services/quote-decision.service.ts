import { Injectable, signal } from '@angular/core';
import { delay, Observable, of, tap } from 'rxjs';

import type { QuoteRejection, AcceptanceTerms } from '@/app/model/acceptance';
import type { RequestStatus } from '@/app/model/analysis-pipeline';

// A API de Quote é somente-leitura hoje, então aceite e recusa ficam em
// memória durante a sessão. As assinaturas já são as do POST que a aplicação
// Java vai expor.
const LATENCIA_SIMULADA = 450;

export interface RecordedDecision {
  readonly proposalId: string;
  readonly status: RequestStatus;
  readonly recordedAt: Date;
}

@Injectable({ providedIn: 'root' })
export class QuoteDecisionService {
  private readonly registro = signal<ReadonlyMap<string, RecordedDecision>>(new Map());

  readonly decisions = this.registro.asReadonly();

  decisionFor(proposalId: string): RecordedDecision | undefined {
    return this.registro().get(proposalId);
  }

  /** POST /api/quotes/{id}/acceptance */
  aceitar(termo: AcceptanceTerms): Observable<RecordedDecision> {
    return this.registrar(termo.proposalId, 'aprovado-pelo-cliente');
  }

  /** POST /api/quotes/{id}/purchase-order */
  aceitarComOrdemDeCompra(proposalId: string, arquivo: File): Observable<RecordedDecision> {
    void arquivo;
    return this.registrar(proposalId, 'aprovado-pelo-cliente');
  }

  /** POST /api/quotes/{id}/sample-shipment */
  confirmSampleShipment(proposalId: string): Observable<RecordedDecision> {
    return this.registrar(proposalId, 'aguardando-amostra');
  }

  /** POST /api/quotes/{id}/rejection */
  recusar(recusa: QuoteRejection): Observable<RecordedDecision> {
    return this.registrar(recusa.proposalId, 'recusado');
  }

  private registrar(
    proposalId: string,
    status: RequestStatus,
  ): Observable<RecordedDecision> {
    const decisions: RecordedDecision = { proposalId, status, recordedAt: new Date() };
    return of(decisions).pipe(
      delay(LATENCIA_SIMULADA),
      // Só grava depois que o servidor confirma: falha na integração não pode
      // deixar a tela dizendo que o orçamento foi aceito (HU-09, critério 7).
      tap((valor) => this.registro.update((atual) => new Map(atual).set(proposalId, valor))),
    );
  }
}
