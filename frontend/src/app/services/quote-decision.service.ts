import { Injectable, signal } from '@angular/core';
import { delay, Observable, of, tap } from 'rxjs';

import type { RecusaDeOrcamento, TermoDeAceite } from '@/app/model/acceptance';
import type { SituacaoDaSolicitacao } from '@/app/model/analysis-pipeline';

// A API de Quote é somente-leitura hoje, então aceite e recusa ficam em
// memória durante a sessão. As assinaturas já são as do POST que a aplicação
// Java vai expor.
const LATENCIA_SIMULADA = 450;

export interface DecisaoRegistrada {
  readonly propostaId: string;
  readonly situacao: SituacaoDaSolicitacao;
  readonly registradaEm: Date;
}

@Injectable({ providedIn: 'root' })
export class QuoteDecisionService {
  private readonly registro = signal<ReadonlyMap<string, DecisaoRegistrada>>(new Map());

  readonly decisoes = this.registro.asReadonly();

  decisaoDe(propostaId: string): DecisaoRegistrada | undefined {
    return this.registro().get(propostaId);
  }

  /** POST /api/quotes/{id}/acceptance */
  aceitar(termo: TermoDeAceite): Observable<DecisaoRegistrada> {
    return this.registrar(termo.propostaId, 'aprovado-pelo-cliente');
  }

  /** POST /api/quotes/{id}/purchase-order */
  aceitarComOrdemDeCompra(propostaId: string, arquivo: File): Observable<DecisaoRegistrada> {
    void arquivo;
    return this.registrar(propostaId, 'aprovado-pelo-cliente');
  }

  /** POST /api/quotes/{id}/rejection */
  recusar(recusa: RecusaDeOrcamento): Observable<DecisaoRegistrada> {
    return this.registrar(recusa.propostaId, 'recusado');
  }

  private registrar(
    propostaId: string,
    situacao: SituacaoDaSolicitacao,
  ): Observable<DecisaoRegistrada> {
    const decisao: DecisaoRegistrada = { propostaId, situacao, registradaEm: new Date() };
    return of(decisao).pipe(
      delay(LATENCIA_SIMULADA),
      // Só grava depois que o servidor confirma: falha na integração não pode
      // deixar a tela dizendo que o orçamento foi aceito (HU-09, critério 7).
      tap((valor) => this.registro.update((atual) => new Map(atual).set(propostaId, valor))),
    );
  }
}
