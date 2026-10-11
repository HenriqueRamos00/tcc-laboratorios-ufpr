import { FormControl } from '@angular/forms';

import { cnpjValido, cnpjValidator, ufBrasileiraValidator } from './empresa-validation';

describe('Validações de empresa', () => {
  it('aceita CNPJ com máscara ou somente dígitos e valida os dígitos verificadores', () => {
    expect(cnpjValido('04.368.898/0001-06')).toBeTrue();
    expect(cnpjValido('04368898000106')).toBeTrue();
    expect(cnpjValido('04.368.898/0001-07')).toBeFalse();
  });

  it('rejeita CNPJ com tamanho incorreto e sequências repetidas', () => {
    expect(cnpjValido('123')).toBeFalse();
    expect(cnpjValido('11.111.111/1111-11')).toBeFalse();
    expect(cnpjValido('AB.368.898/0001-06')).toBeFalse();
  });

  it('rejeita o CNPJ informado com 14 dígitos quando os verificadores estão incorretos', () => {
    expect(cnpjValido('12.345.678/0001-90')).toBeFalse();
    expect(cnpjValido('12.345.678/0001-95')).toBeTrue();
  });

  it('valida o formato nacional da UF e permite o campo vazio ser tratado por required', () => {
    expect(ufBrasileiraValidator(new FormControl('PR'))).toBeNull();
    expect(ufBrasileiraValidator(new FormControl('xx'))).toEqual({ ufBrasileira: true });
    expect(cnpjValidator(new FormControl(''))).toBeNull();
  });
});
