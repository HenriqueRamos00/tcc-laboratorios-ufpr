// IEC 60599, adotada no Brasil como ABNT NBR 7274 - um módulo para as duas,
// porque é um método só visto em duas telas: a tabela de códigos de falha e o
// gráfico de concentração por gás.
//
// PROVENIÊNCIA E GRAU DE CONFIANÇA (leia antes de mexer num número):
//
// O que está aqui: quais razões características o método lê, quais gases de
// falha ele acompanha, e os códigos de falha que ele define. Nada disso é
// número de fronteira.
//
// O que NÃO está aqui, e por que não foi escrito de memória:
//
// 1. A faixa de cada razão que leva a um código de falha (Tabela 1 da
//    IEC 60599). Nenhuma fonte citável dessa tabela foi lida nesta auditoria, e
//    ela tem entradas "NS" (não significativo) e faixas que não cobrem o
//    domínio inteiro - transcrever de memória produziria veredito confiante
//    sobre fronteira inventada. As razões saem medidas e o código sai nulo com
//    o motivo escrito.
// 2. O valor-limite por gás (valores típicos do Anexo A da IEC 60599). São
//    faixas de percentil que dependem do tipo de equipamento e do histórico da
//    frota, o AutoLAB não traz esse cadastro, e o laudo do Lactec não publica o
//    limite do gás como publica o da físico-química. O gráfico mostra o medido
//    e declara o limite como não avaliado.

import { DESCRICAO_DE_DUVAL_1, type CodigoDeDuval1 } from './duval-1';
import type { GasRatioDefinition } from './gas-ratio';
import type { FaultCode } from './health';

/**
 * As três razões características do método, na ordem da tabela da norma.
 */
export const IEC_60599_RATIOS: readonly GasRatioDefinition[] = [
  {
    key: 'c2h2-por-c2h4',
    numerator: { key: 'c2h2', formula: 'C₂H₂' },
    denominator: { key: 'c2h4', formula: 'C₂H₄' },
  },
  {
    key: 'ch4-por-h2',
    numerator: { key: 'ch4', formula: 'CH₄' },
    denominator: { key: 'h2', formula: 'H₂' },
  },
  {
    key: 'c2h4-por-c2h6',
    numerator: { key: 'c2h4', formula: 'C₂H₄' },
    denominator: { key: 'c2h6', formula: 'C₂H₆' },
  },
];

/**
 * Os gases de falha que o método acompanha. O₂ e N₂ ficam fora: são indicadores
 * de entrada de ar, não gases de falha, e as suas dezenas de milhares de ppm
 * achatariam a escala de todos os outros no mesmo gráfico.
 */
export const IEC_60599_FAULT_GASES: readonly { readonly key: string; readonly formula: string }[] = [
  { key: 'h2', formula: 'H₂' },
  { key: 'ch4', formula: 'CH₄' },
  { key: 'c2h6', formula: 'C₂H₆' },
  { key: 'c2h4', formula: 'C₂H₄' },
  { key: 'c2h2', formula: 'C₂H₂' },
  { key: 'co', formula: 'CO' },
  { key: 'co2', formula: 'CO₂' },
];

// PROVENIÊNCIA: a lista de códigos e as descrições saem do dicionário do triângulo
// de Duval 1 (`DESCRICAO_DE_DUVAL_1`), reusadas por conveniência de legenda. O texto
// da IEC 60599 NÃO foi lido de fonte citável, então esta legenda não afirma ser a
// nomenclatura da norma: ela explica o que cada sigla quer dizer para quem lê o laudo.
// Confiança BAIXA quanto à redação, e ela não decide veredito nenhum, porque o código
// de falha não é atribuído por este projeto.
//
// DT fica de fora porque é zona do triângulo de Duval (falha mista térmica e elétrica),
// e não código de falha; incluí-lo faria a legenda prometer um código a mais.
export const IEC_60599_FAULT_CODES: readonly FaultCode[] = (
  Object.keys(DESCRICAO_DE_DUVAL_1) as CodigoDeDuval1[]
)
  .filter((code) => code !== 'DT')
  .map((code) => ({ code, description: DESCRICAO_DE_DUVAL_1[code] }));

export const IEC_60599_FAULT_CODE_NOT_ASSESSED =
  'A faixa de cada razão que leva a um código de falha está na Tabela 1 da IEC 60599, ' +
  'adotada pela NBR 7274, e não está transcrita no projeto: as razões saem medidas, o ' +
  'código de falha não é atribuído.';

export const NBR_7274_LIMITS_NOT_ASSESSED =
  'O valor-limite por gás depende do tipo de equipamento e do histórico da frota, e não ' +
  'está no projeto: o AutoLAB entrega a concentração medida, não a faixa.';
