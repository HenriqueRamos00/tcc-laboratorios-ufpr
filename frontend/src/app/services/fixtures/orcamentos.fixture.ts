// Isto vai compilado no bundle, então os e-mails usam o domínio reservado para
// documentação (RFC 2606): endereço de pessoa real não pode entrar aqui.
//
// Os `stage` foram escolhidos para a demonstração percorrer os três recortes da
// linha: proposta, amostras e laboratório.

import type { Quote } from '@/app/model/quote';

export const ORCAMENTOS_DE_DEMONSTRACAO: readonly Quote[] = [
  {
    id: 'EAQ_2026_33322_V_3',
    code: 'EAQ_2026_33322_V_3',
    name: 'Análise de polímeros',
    status: 'Aberto',
    stage: 'Qualificação',
    description:
      'Caracterização de materiais poliméricos conforme as normas aplicáveis, com preparo de ' +
      'corpos de prova e ensaio mecânico.',
    companyName: 'ENGIE Brasil',
    externalContactName: 'Bruno Pereira',
    externalContactEmail: 'bruno.pereira@exemplo.com.br',
    totalPrice: 0,
    createdDate: '2026-11-20T09:00:00.000+0000',
  },
  {
    id: 'EAQ_2026_45482_V_1',
    code: 'EAQ_2026_45482_V_1',
    name: 'Análise de óleo isolante',
    status: 'Pendente de Aceite',
    stage: 'Negociação',
    description:
      'No orçamento abaixo detectamos as necessidades do seu negócio para que seja feita uma ' +
      'análise completa de suas amostras:',
    companyName: 'Copel Distribuição',
    externalContactName: 'Camila Souza',
    externalContactEmail: 'camila@exemplo.com.br',
    totalPrice: 56292.3,
    createdDate: '2026-10-15T09:00:00.000+0000',
  },
  {
    id: 'EAQ_2026_45490_V_2',
    code: 'EAQ_2026_45490_V_2',
    name: 'Ensaio de materiais compósitos',
    status: 'Em Andamento',
    stage: 'Fechado Ganho',
    description: 'Ensaio de tração e flexão em materiais compósitos, com relatório técnico.',
    companyName: 'Petrobras',
    externalContactName: 'Pedro bom de Bola',
    externalContactEmail: 'pedro@exemplo.com.br',
    totalPrice: 18200,
    createdDate: '2026-10-18T09:00:00.000+0000',
  },
  {
    id: 'EAQ_2026_45490_V_3',
    code: 'EAQ_2026_45490_V_3',
    name: 'Análise de água potável',
    status: 'Finalizado',
    stage: 'Relatório Publicado',
    description: 'Análise físico-química e microbiológica de água potável.',
    companyName: 'Sanepar',
    externalContactName: 'Joselito Bueno',
    externalContactEmail: 'joselito@exemplo.com.br',
    totalPrice: 8500,
    createdDate: '2026-10-20T09:00:00.000+0000',
  },
  {
    id: 'EAQ_2026_45501_V_1',
    code: 'EAQ_2026_45501_V_1',
    name: 'Qualidade do ar - 2° Semestre de 2025',
    status: 'Cancelado',
    stage: 'Fechado Perdido',
    description: 'Monitoramento de qualidade do ar em ponto fixo, com dois ciclos de coleta.',
    companyName: 'Copel Distribuição',
    externalContactName: 'Bruno Pereira',
    externalContactEmail: 'bruno.pereira@exemplo.com.br',
    totalPrice: 12400,
    createdDate: '2026-09-30T09:00:00.000+0000',
  },
];
