// Razões de Rogers.
//
// PROVENIÊNCIA E GRAU DE CONFIANÇA (leia antes de mexer num número):
//
// O método lê quatro razões entre gases e traduz as faixas delas em um código
// de falha. As quatro razões são as que estão aqui, e isso é consenso de toda
// fonte secundária consultada.
//
// A TABELA DE FAIXAS NÃO ESTÁ AQUI. A versão de 1978 e a que a IEEE
// C57.104-1991 republicou divergem entre si no número de razões e nos cortes, e
// nenhuma das duas foi lida de fonte citável nesta auditoria. Escrever os
// cortes de memória daria código de falha com cara de confiante sobre fronteira
// inventada, que é exatamente o defeito que esta tela já teve quando desenhava
// os três triângulos de Duval para toda amostra.
//
// A tela anterior mostrava uma grade de gás por gás, pintada de "térmico" ou
// "descarga" célula a célula, com valores escritos na massa de demonstração.
// Aquela grade não é o método: Rogers não classifica par de gases, classifica a
// faixa de cada razão. Por isso ela saiu, e no lugar ficaram as razões medidas.

import type { GasRatioDefinition } from './gas-ratio';

export const ROGERS_RATIOS: readonly GasRatioDefinition[] = [
  {
    key: 'ch4-por-h2',
    numerator: { key: 'ch4', formula: 'CH₄' },
    denominator: { key: 'h2', formula: 'H₂' },
  },
  {
    key: 'c2h6-por-ch4',
    numerator: { key: 'c2h6', formula: 'C₂H₆' },
    denominator: { key: 'ch4', formula: 'CH₄' },
  },
  {
    key: 'c2h4-por-c2h6',
    numerator: { key: 'c2h4', formula: 'C₂H₄' },
    denominator: { key: 'c2h6', formula: 'C₂H₆' },
  },
  {
    key: 'c2h2-por-c2h4',
    numerator: { key: 'c2h2', formula: 'C₂H₂' },
    denominator: { key: 'c2h4', formula: 'C₂H₄' },
  },
];

export const ROGERS_FAULT_CODE_NOT_ASSESSED =
  'A tabela que traduz as faixas das razões de Rogers em código de falha não está ' +
  'transcrita no projeto: as razões saem medidas, o código de falha não é atribuído.';
