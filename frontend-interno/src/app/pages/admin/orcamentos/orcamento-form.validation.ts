import { AbstractControl, ValidationErrors } from '@angular/forms';

import { converterValorParaCentavosSeguro } from './orcamento-money';

export function textoObrigatorioSemEspacos(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { required: true };
}

export function inteiroMinimoUm(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || value === '') return null;
  return Number.isInteger(Number(value)) ? null : { inteiro: true };
}

export function ateDuasCasasDecimais(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || value === '') return null;

  const number = Number(value);
  if (!Number.isFinite(number)) return { numeroValido: true };

  if (!Number.isSafeInteger(Math.round(number * 100))) return { valorInseguro: true };
  return converterValorParaCentavosSeguro(number) === null ? { casasDecimais: true } : null;
}
