import type { ConclusaoDaAnalise } from '@/app/model/report';

// Um PDF de uma página, suficiente para o navegador abrir o arquivo baixado
// enquanto o AutoLAB não serve o laudo de verdade.
export const PDF_DE_EXEMPLO = [
  '%PDF-1.4',
  '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj',
  '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj',
  '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >> endobj',
  'trailer << /Root 1 0 R >>',
  '%%EOF',
].join('\n');

export const CONCLUSAO: ConclusaoDaAnalise = {
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
