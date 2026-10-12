// IEEE Std C57.104 - total de gases combustíveis.
//
// PROVENIÊNCIA E GRAU DE CONFIANÇA (leia antes de mexer num número):
//
// O que está aqui é a composição do total de gases combustíveis: os seis gases
// abaixo, e só eles. CO₂ fica fora porque não é combustível, e O₂ e N₂ ficam
// fora porque são indicadores de entrada de ar. A composição é consenso de toda
// fonte consultada e o próprio laudo do Lactec publica o totalizador com esse
// nome.
//
// OS LIMIARES DE ATENÇÃO E ALARME NÃO ESTÃO AQUI. Na C57.104-2019 eles vêm de
// tabelas de percentil que dependem do tipo do transformador, da idade e de a
// unidade ter ou não respiro, e o AutoLAB não tem cadastro de equipamento para
// responder nada disso (ver CLAUDE.md: não existem TAG de placa, tipo,
// localização, tensão nem potência). Escrever um par de números de memória
// pintaria faixa de severidade que ninguém atribuiu, então a série sai medida e
// os dois limiares saem nulos com o motivo escrito.

export interface CombustibleGases {
  readonly h2: number | null;
  readonly ch4: number | null;
  readonly c2h6: number | null;
  readonly c2h4: number | null;
  readonly c2h2: number | null;
  readonly co: number | null;
}

interface CombustibleGas {
  readonly key: keyof CombustibleGases;
  readonly formula: string;
}

export const COMBUSTIBLE_GASES: readonly CombustibleGas[] = [
  { key: 'h2', formula: 'H₂' },
  { key: 'ch4', formula: 'CH₄' },
  { key: 'c2h6', formula: 'C₂H₆' },
  { key: 'c2h4', formula: 'C₂H₄' },
  { key: 'c2h2', formula: 'C₂H₂' },
  { key: 'co', formula: 'CO' },
];

export const IEEE_THRESHOLDS_NOT_ASSESSED =
  'Os limiares de atenção e alarme da IEEE C57.104 dependem do tipo e da idade do ' +
  'transformador, cadastro que o AutoLAB não tem, e não estão no projeto: a evolução sai ' +
  'medida e as faixas não são desenhadas.';

/** Qual gás combustível ficou sem ensaio, para a tela dizer por que não somou. */
export function combustibleGasWithoutTest(gases: CombustibleGases): string | null {
  const missing = COMBUSTIBLE_GASES.find((gas) => gases[gas.key] == null);
  return missing ? missing.formula : null;
}

/**
 * Soma dos seis gases combustíveis, em ppm. Devolve `null` quando algum deles
 * não foi ensaiado: somar o que foi medido daria um total menor que o real, e
 * um total menor que o real comparado com limiar de alarme é falso negativo. É
 * a mesma regra da conferência de totalizador do laudo, que tem três desfechos
 * e não dois.
 */
export function totalCombustibleGases(gases: CombustibleGases): number | null {
  let total = 0;
  for (const gas of COMBUSTIBLE_GASES) {
    const concentration = gases[gas.key];
    if (concentration == null) return null;
    total += concentration;
  }
  return total;
}
