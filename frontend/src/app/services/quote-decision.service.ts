import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { map, Observable, tap } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type { QuoteRejection, AcceptanceTerms } from '@/app/model/acceptance';
import type { RequestStatus } from '@/app/model/analysis-pipeline';
import { statusOfClientDecision, type ClientDecision } from '@/app/model/quote';

import { AcceptanceDraftService } from './acceptance-draft.service';

export interface RecordedDecision {
  readonly proposalId: string;
  readonly status: RequestStatus;
  readonly recordedAt: Date;
}

@Injectable({ providedIn: 'root' })
export class QuoteDecisionService {
  private readonly http = inject(HttpClient);
  private readonly drafts = inject(AcceptanceDraftService);
  private readonly baseUrl = `${API_URL}/api/quotes`;
  private readonly registro = signal<ReadonlyMap<string, RecordedDecision>>(new Map());

  readonly decisions = this.registro.asReadonly();

  decisionFor(proposalId: string): RecordedDecision | undefined {
    return this.registro().get(proposalId);
  }

  aceitar(termo: AcceptanceTerms): Observable<RecordedDecision> {
    return this.registrar(termo.proposalId, 'acceptance', {
      billing: termo.billing,
      approval: termo.approval,
    });
  }

  aceitarComOrdemDeCompra(proposalId: string, arquivo: File): Observable<RecordedDecision> {
    const form = new FormData();
    form.append('file', arquivo, arquivo.name);
    return this.registrar(proposalId, 'purchase-order', form);
  }

  confirmSampleShipment(proposalId: string): Observable<RecordedDecision> {
    return this.registrar(proposalId, 'sample-shipment', null);
  }

  recusar(recusa: QuoteRejection): Observable<RecordedDecision> {
    return this.registrar(recusa.proposalId, 'rejection', { justification: recusa.justification });
  }

  private registrar(
    proposalId: string,
    route: 'acceptance' | 'purchase-order' | 'sample-shipment' | 'rejection',
    body: unknown,
  ): Observable<RecordedDecision> {
    return this.http
      .post<ClientDecision>(`${this.baseUrl}/${encodeURIComponent(proposalId)}/${route}`, body)
      .pipe(
        map((decision) => ({
          proposalId,
          status: statusOfClientDecision(decision.kind),
          recordedAt: new Date(decision.recordedAt),
        })),
        tap((valor) => {
          this.registro.update((atual) => new Map(atual).set(proposalId, valor));
          this.drafts.discard(proposalId);
        }),
      );
  }
}
