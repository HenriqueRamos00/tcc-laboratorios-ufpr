import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const FORMATO_CNPJ = /^(\d{14}|\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2})$/;
const CNPJ_COM_DIGITOS_REPETIDOS = /^([0-9])\1{13}$/;
const PESOS_PRIMEIRO_DIGITO = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
const PESOS_SEGUNDO_DIGITO = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];

export function somenteDigitos(value: string): string {
  return value.replace(/\D/g, '');
}

export function formatarCnpj(value: string): string {
  const digits = somenteDigitos(value);
  if (digits.length !== 14) return value;
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
}

export function cnpjValido(value: string): boolean {
  const candidate = value.trim();
  if (!FORMATO_CNPJ.test(candidate)) return false;
  const digits = somenteDigitos(candidate);
  if (CNPJ_COM_DIGITOS_REPETIDOS.test(digits)) return false;

  const base = digits.slice(0, 12);
  const primeiro = calcularDigito(base, PESOS_PRIMEIRO_DIGITO);
  const segundo = calcularDigito(base + primeiro, PESOS_SEGUNDO_DIGITO);
  return digits.endsWith(`${primeiro}${segundo}`);
}

function calcularDigito(base: string, pesos: readonly number[]): number {
  const soma = [...base].reduce(
    (total, digito, index) => total + Number(digito) * pesos[index],
    0,
  );
  const resto = soma % 11;
  return resto < 2 ? 0 : 11 - resto;
}

export const cnpjValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (!value) return null;
  return cnpjValido(value) ? null : { cnpj: true };
};
