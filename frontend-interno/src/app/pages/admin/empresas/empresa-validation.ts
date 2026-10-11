import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

const UF_BRASILEIRAS = new Set([
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB',
  'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
]);

export const ESTADOS_BRASILEIROS = [...UF_BRASILEIRAS];

const FORMATO_CNPJ = /^(\d{14}|\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2})$/;
const CNPJ_COM_DIGITOS_REPETIDOS = /^([0-9])\1{13}$/;
const TAMANHO_BASE_CNPJ = 12;
const PESOS_PRIMEIRO_DIGITO = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
const PESOS_SEGUNDO_DIGITO = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];

export function somenteDigitos(value: string): string {
  return value.replace(/\D/g, '');
}

export function cnpjValido(value: string): boolean {
  const candidate = value.trim();
  if (!FORMATO_CNPJ.test(candidate)) return false;
  const digits = somenteDigitos(candidate);
  if (CNPJ_COM_DIGITOS_REPETIDOS.test(digits)) return false;

  const base = digits.slice(0, TAMANHO_BASE_CNPJ);
  const primeiro = calcularDigito(base, PESOS_PRIMEIRO_DIGITO);
  const segundo = calcularDigito(base + primeiro, PESOS_SEGUNDO_DIGITO);
  return digits.endsWith(`${primeiro}${segundo}`);
}

function calcularDigito(base: string, pesos: readonly number[]): number {
  const soma = [...base].reduce((total, digito, index) => total + Number(digito) * pesos[index], 0);
  const resto = soma % 11;
  return resto < 2 ? 0 : 11 - resto;
}

export const cnpjValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (!value) return null;
  return cnpjValido(value) ? null : { cnpj: true };
};

export const ufBrasileiraValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim().toUpperCase() : '';
  if (!value) return null;
  return UF_BRASILEIRAS.has(value) ? null : { ufBrasileira: true };
};

export const trimmedRequired = [Validators.required, Validators.pattern(/.*\S.*/)];
