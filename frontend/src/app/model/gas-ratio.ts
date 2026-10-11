// Razão característica entre dois gases: o insumo comum da IEC 60599, da
// NBR 7274 e de Rogers.
//
// A razão é aritmética sobre o valor medido e não depende de norma nenhuma;
// quais razões cada método lê é que é normativo, e isso mora no módulo do
// método. Aqui ficam só a conta e os dois motivos de ela não existir.

import type { GasRatioReading } from './health';

export interface GasInRatio {
  readonly key: string;
  readonly formula: string;
}

export interface GasRatioDefinition {
  readonly key: string;
  readonly numerator: GasInRatio;
  readonly denominator: GasInRatio;
}

/**
 * Calcula a razão de uma coleta.
 *
 * Dois desfechos sem valor, e são diferentes: gás sem ensaio é falta de dado, e
 * denominador zero é razão que não existe. O segundo caso é comum de verdade,
 * porque "ND" entra como zero: dividir por ele devolveria infinito, e infinito
 * num comparador de faixa vira o diagnóstico mais grave da tabela sem ninguém
 * ter medido nada.
 */
export function gasRatio(
  definition: GasRatioDefinition,
  concentrations: Readonly<Record<string, number | null>>,
): GasRatioReading {
  const label = `${definition.numerator.formula} / ${definition.denominator.formula}`;
  const numerator = concentrations[definition.numerator.key] ?? null;
  const denominator = concentrations[definition.denominator.key] ?? null;

  if (numerator === null || denominator === null) {
    const missing = numerator === null ? definition.numerator : definition.denominator;

    return { kind: 'withoutTest', key: definition.key, label, missingGas: missing.formula };
  }

  if (denominator === 0) {
    return {
      kind: 'denominatorNotDetected',
      key: definition.key,
      label,
      denominator: definition.denominator.formula,
    };
  }

  return { kind: 'computed', key: definition.key, label, value: numerator / denominator };
}
