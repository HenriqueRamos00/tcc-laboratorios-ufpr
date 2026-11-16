import type { TourDefinition, TourStep } from '@/app/model/tour';

export type HealthTourView = 'fisico-quimico' | 'criterios' | 'metodos';

const CONFORMITY_TEXT: Record<Exclude<HealthTourView, 'metodos'>, string> = {
  'fisico-quimico':
    'O indicador resume a condição do óleo isolante segundo os ensaios físico-químicos: normal, em alerta ou não conforme.',
  criterios:
    'O indicador resume a condição do equipamento segundo os gases dissolvidos no óleo: normal, em alerta ou não conforme.',
};

const EVOLUTION_TEXT: Record<Exclude<HealthTourView, 'metodos'>, string> = {
  'fisico-quimico':
    'O gráfico mostra como cada ensaio físico-químico evoluiu a cada coleta. Uma mudança de tendência costuma aparecer aqui antes de virar não conformidade.',
  criterios:
    'O gráfico mostra a evolução de cada gás a cada coleta, em escala logarítmica para que gases de concentrações muito diferentes caibam juntos.',
};

function resultSteps(view: Exclude<HealthTourView, 'metodos'>): TourStep[] {
  const steps: TourStep[] = [
    {
      id: 'conformidade',
      target: 'indicadores-conformidade',
      side: 'bottom',
      title: 'Conformidade',
      text: CONFORMITY_TEXT[view],
    },
    {
      id: 'evolucao',
      target: 'indicadores-evolucao',
      side: 'top',
      title: 'Evolução',
      text: EVOLUTION_TEXT[view],
    },
  ];

  if (view === 'fisico-quimico') {
    steps.push({
      id: 'resultados',
      target: 'indicadores-resultados',
      side: 'top',
      title: 'Resultados por ensaio',
      text: 'A tabela compara cada ensaio com o valor-limite da norma, mostra o histórico por data de coleta e classifica o resultado mais recente. ND marca ensaio não detectado.',
    });
  }

  steps.push({
    id: 'conclusao',
    target: 'indicadores-conclusao',
    side: 'top',
    title: 'Conclusão do relatório',
    text: 'Aqui está a conclusão escrita pelo laboratório para a análise mais recente desta aba.',
  });

  return steps;
}

function viewSteps(view: HealthTourView): TourStep[] {
  const gasCriteria: TourStep = {
    id: 'gases-criterios',
    target: 'indicadores-gases-criterios',
    side: 'bottom',
    title: 'Critério de análise',
    text: '"Critérios LACTEC" mostra a conformidade e a evolução dos gases. "Métodos de Diagnóstico" aplica os métodos clássicos de interpretação aos mesmos gases. Esta ajuda explica o critério que estiver aberto.',
  };

  if (view === 'fisico-quimico') return resultSteps(view);
  if (view === 'criterios') return [gasCriteria, ...resultSteps(view)];
  return [
    gasCriteria,
    {
      id: 'metodos',
      target: 'indicadores-metodos',
      side: 'top',
      title: 'Métodos de diagnóstico',
      text: 'Cada método cruza as proporções entre os gases para indicar o tipo provável de falha, como descargas ou sobreaquecimento. Métodos diferentes podem divergir: compare os resultados.',
    },
  ];
}

export function healthIndicatorsTour(view: HealthTourView): TourDefinition {
  return {
    id: 'indicadores-de-saude',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'abas',
        target: 'indicadores-abas',
        side: 'bottom',
        title: 'Tipo de ensaio',
        text: 'Alterne entre os ensaios Físico-Químicos e os Gases Dissolvidos. Uma aba fica travada quando não há análise daquele tipo. Esta ajuda explica a aba que estiver aberta.',
      },
      ...viewSteps(view),
      {
        id: 'baixar',
        target: 'indicadores-baixar',
        side: 'bottom',
        title: 'Baixar relatório',
        text: 'Baixa o relatório histórico com os indicadores deste equipamento.',
      },
    ],
  };
}
