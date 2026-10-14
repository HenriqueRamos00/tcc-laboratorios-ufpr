// O que a janela de status conta ao cliente em cada etapa. O texto mora no
// modelo, e não na tela, porque quem o define é o processo: a mesma etapa
// significa a mesma coisa em qualquer lugar que a explique, e etapa que muda
// de redação muda em um arquivo só.
//
// Etapa sem aviso é etapa que a linha do stepper já explica sozinha: o cliente
// não tem nada a fazer e nada a esperar além do andamento. O mapa é completo
// por construção, e `null` é a decisão explícita de não ter aviso: etapa nova
// sem linha aqui vira erro de compilação, em vez de chegar muda à tela.

import type { PipelineStage } from './analysis-pipeline';
import type { ShippingGuidance } from './sample-shipping';

export interface StageNotice {
  readonly title: string;
  readonly text: string;
  /** Rótulo do botão que leva ao orçamento, só onde há ação a tomar lá dentro. */
  readonly callToAction?: string;
  /** Orientação de envio da amostra, quando a etapa já pede que o cliente a despache. */
  readonly shipping?: ShippingGuidance;
}

const VIEW_THE_QUOTE = 'Ver o orçamento';

const NOTICE_BY_STAGE: Record<PipelineStage, StageNotice | null> = {
  qualificacao: {
    title: 'Estamos procurando a área que vai analisar seu pedido',
    text:
      'Sua solicitação já está em nossas mãos. Agora procuramos, dentro do Lactec, a área de ' +
      'laboratório capaz de executar o que você pediu e avaliamos a viabilidade do projeto. ' +
      'Assim que a área for escolhida, sua solicitação passa para Em Análise pela Área.',
  },
  'analise-da-area': {
    title: 'Estamos em busca da melhor solução para seu negócio',
    text:
      'Nossos profissionais estão validando qual é o laboratório capaz de solucionar e trazer ' +
      'resultados para cada uma das análises requisitadas. Em breve seu orçamento ficará ' +
      'disponível por meio desta plataforma!',
  },
  'elaborando-proposta': {
    title: 'Encontramos a área responsável pelo seu projeto',
    text:
      'O laboratório confirmou que temos a capacidade de realizar as análises solicitadas. ' +
      'Agora nossa equipe apura os custos de execução e descreve, no orçamento, tudo o que ' +
      'será feito pelo valor apresentado.',
  },
  negociacao: {
    title: 'Aguardando sua revisão',
    text:
      'Esta proposta técnica está pronta para execução. Por favor, revise os termos e custos ' +
      'associados para prosseguir com a análise laboratorial.',
    callToAction: VIEW_THE_QUOTE,
  },
  'aprovado-pelo-cliente': {
    title: 'Orçamento aprovado. Vamos trabalhar juntos!',
    text:
      'O Lactec agradece a confiança e já se prepara para receber suas amostras. Envie o ' +
      'material para o endereço abaixo e, depois de despachar, abra o orçamento para ' +
      'confirmar o envio.',
    callToAction: VIEW_THE_QUOTE,
    shipping: 'address-and-labeling',
  },
  'elaborando-relatorio': {
    title: 'Seus ensaios foram concluídos e os dados estão em análise',
    text:
      'As análises laboratoriais da sua amostra já foram executadas e os resultados, ' +
      'validados. Agora um de nossos pesquisadores interpreta esses data e redige o laudo ' +
      'técnico. Publicado o relatório, ele fica disponível para você nesta plataforma.',
  },
  'aguardando-amostra': {
    title: 'Aguardando a chegada da sua amostra',
    text:
      'Seu material está a caminho do laboratório. Enquanto ele não chega, confira se a caixa ' +
      'saiu identificada: a etiqueta acelera o protocolo e adianta a entrega dos seus resultados.',
    shipping: 'labeling',
  },
  recebido: null,
  protocolado: null,
  'em-execucao': null,
  incompleto: null,
  completo: null,
  validado: null,
  finalizado: null,
  'relatorio-publicado': null,
};

export function noticeFor(stage: PipelineStage): StageNotice | null {
  return NOTICE_BY_STAGE[stage];
}
