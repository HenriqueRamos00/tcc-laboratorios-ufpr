import { inject, Injectable } from '@angular/core';
import { delay, map, Observable, of } from 'rxjs';

import type { AnalysisConclusion, PublishedReport } from '@/app/model/report';
import { QuotesService } from './quotes.service';

const LATENCIA_SIMULADA = 300;

// Um PDF de uma página, suficiente para o navegador abrir o arquivo baixado
// enquanto o AutoLAB não serve o laudo de verdade.
const PDF_DE_EXEMPLO = [
  '%PDF-1.4',
  '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj',
  '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj',
  '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >> endobj',
  'trailer << /Root 1 0 R >>',
  '%%EOF',
].join('\n');

// Massa de demonstração enquanto o AutoLAB não publica o laudo pelo portal. Os
// identificadores de orçamento são os mesmos que o repositório de orçamentos
// entrega na etapa "Relatório publicado": é esse vínculo que faz a tela de um
// orçamento mostrar os laudos dele, e não os de todo mundo.
const ANALYSIS_CONCLUSIONS: readonly AnalysisConclusion[] = [
  {
    quoteId: 'quote-15',
    quoteCode: 'EAQ_2026_45496_V_1',
    protocol: '00307/2026',
    testType: 'Ensaios Físico-Químicos em Óleo Isolante',
    summary:
      'O processo de análise foi concluído com sucesso. Todos os parâmetros técnicos foram ' +
      'validados pela curadoria laboratorial e o laudo final está disponível para visualização.',
    reports: [
      {
        id: 'relatorio-00307-1',
        code: 'LACTEC-00307/2026',
        protocol: '00307/2026',
        sample: 'Transformador TR-01',
        laboratory: 'Lab. de Óleos',
        publishedOn: '07/03/2026',
      },
      {
        id: 'relatorio-00307-2',
        code: 'LACTEC-00308/2026',
        protocol: '00307/2026',
        sample: 'Transformador TR-02',
        laboratory: 'Lab. de Óleos',
        publishedOn: '07/03/2026',
      },
      {
        id: 'relatorio-00307-3',
        code: 'LACTEC-00309/2026',
        protocol: '00307/2026',
        sample: 'Transformador TR-03',
        laboratory: 'Lab. de Óleos',
        publishedOn: '09/03/2026',
      },
    ],
  },
  {
    quoteId: 'quote-18',
    quoteCode: 'EAQ_2026_45499_V_1',
    protocol: '00412/2026',
    testType: 'Cromatografia de Gases Dissolvidos',
    summary:
      'A cromatografia dos quatro transformadores foi concluída e validada pela curadoria ' +
      'laboratorial. Os laudos estão disponíveis para visualização e download.',
    reports: [
      {
        id: 'relatorio-00412-1',
        code: 'LACTEC-00412/2026',
        protocol: '00412/2026',
        sample: 'Transformador TR-11',
        laboratory: 'Lab. de Óleos',
        publishedOn: '22/08/2026',
      },
      {
        id: 'relatorio-00412-2',
        code: 'LACTEC-00413/2026',
        protocol: '00412/2026',
        sample: 'Transformador TR-12',
        laboratory: 'Lab. de Óleos',
        publishedOn: '22/08/2026',
      },
      {
        id: 'relatorio-00412-3',
        code: 'LACTEC-00414/2026',
        protocol: '00412/2026',
        sample: 'Transformador TR-13',
        laboratory: 'Lab. de Óleos',
        publishedOn: '24/08/2026',
      },
      {
        id: 'relatorio-00412-4',
        code: 'LACTEC-00415/2026',
        protocol: '00412/2026',
        sample: 'Transformador TR-14',
        laboratory: 'Lab. de Óleos',
        publishedOn: '24/08/2026',
      },
    ],
  },
  {
    quoteId: 'quote-19',
    quoteCode: 'EAQ_2026_45500_V_1',
    protocol: '00388/2026',
    testType: 'Ensaio de Rigidez Dielétrica',
    summary:
      'A rigidez dielétrica das amostras foi medida e validada pela curadoria laboratorial. ' +
      'Os laudos estão disponíveis para visualização e download.',
    reports: [
      {
        id: 'relatorio-00388-1',
        code: 'LACTEC-00388/2026',
        protocol: '00388/2026',
        sample: 'Óleo mineral isolante, tanque 1',
        laboratory: 'Lab. de Óleos',
        publishedOn: '06/08/2026',
      },
      {
        id: 'relatorio-00388-2',
        code: 'LACTEC-00389/2026',
        protocol: '00388/2026',
        sample: 'Óleo mineral isolante, tanque 2',
        laboratory: 'Lab. de Óleos',
        publishedOn: '06/08/2026',
      },
    ],
  },
  // Orçamento da antiga massa local; só aparece se a API autorizar esse identificador.
  {
    quoteId: 'EAQ_2026_45490_V_3',
    quoteCode: 'EAQ_2026_45490_V_3',
    protocol: '00501/2026',
    testType: 'Análise Físico-Química e Microbiológica de Água',
    summary:
      'A análise da água dos dois pontos de coleta foi concluída e validada pela curadoria ' +
      'laboratorial. Os laudos estão disponíveis para visualização e download.',
    reports: [
      {
        id: 'relatorio-00501-1',
        code: 'LACTEC-00501/2026',
        protocol: '00501/2026',
        sample: 'Água, ponto de coleta 1',
        laboratory: 'Lab. de Águas',
        publishedOn: '21/10/2026',
      },
      {
        id: 'relatorio-00501-2',
        code: 'LACTEC-00502/2026',
        protocol: '00501/2026',
        sample: 'Água, ponto de coleta 2',
        laboratory: 'Lab. de Águas',
        publishedOn: '21/10/2026',
      },
    ],
  },
];

const CONCLUSION_BY_QUOTE = new Map<string, AnalysisConclusion>(
  ANALYSIS_CONCLUSIONS.map((conclusion) => [conclusion.quoteId, conclusion]),
);

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly quotes = inject(QuotesService);

  /**
   * GET /api/quotes/{id}/reports
   *
   * Devolve nulo quando o orçamento não tem laudo publicado: a tela precisa
   * distinguir "ainda não há laudo" de "a consulta falhou", e as duas coisas
   * dizem frases diferentes ao cliente.
   */
  analysisConclusion(quoteId: string): Observable<AnalysisConclusion | null> {
    return this.quotes.getQuoteById(quoteId).pipe(
      map(() => CONCLUSION_BY_QUOTE.get(quoteId) ?? null),
    );
  }

  /** GET /api/reports: todos os laudos publicados do cliente, de qualquer orçamento. */
  publishedReports(): Observable<readonly PublishedReport[]> {
    return this.quotes.getQuotes().pipe(
      map((quotes) => quotes.flatMap((quote) => CONCLUSION_BY_QUOTE.get(quote.id)?.reports ?? [])),
    );
  }

  /**
   * GET /api/reports/{id}/file
   *
   * Devolve os bytes do laudo, e não o endereço dele: o arquivo fica atrás do
   * token do portal, então quem baixa é o cliente HTTP, não a barra de
   * endereços do navegador.
   */
  downloadReport(reportId: string): Observable<Blob> {
    void reportId;
    return of(new Blob([PDF_DE_EXEMPLO], { type: 'application/pdf' })).pipe(
      delay(LATENCIA_SIMULADA),
    );
  }
}
