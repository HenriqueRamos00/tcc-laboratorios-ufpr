import { HttpErrorResponse } from '@angular/common/http';

import { PasswordPolicy } from './profile';
import { describeProfileError, passwordRequirements } from './profile-presentation';

describe('Apresentação das regras do perfil', () => {
  it('apresenta requisitos de senha específicos da fonte', () => {
    const policy: PasswordPolicy = {
      minCodePoints: 8,
      maxCharacters: 72,
      maxUtf8Bytes: null,
      requiresUppercase: true,
      requiresLowercase: true,
      requiresDigit: true,
      rejectsBlank: true,
    };

    expect(passwordRequirements('Abcdef12', policy).every((requirement) => requirement.satisfied)).toBeTrue();
    expect(passwordRequirements('abcdef12', policy).find((requirement) => requirement.key === 'uppercase')?.satisfied).toBeFalse();
    expect(passwordRequirements('        ', policy).find((requirement) => requirement.key === 'nonblank')?.satisfied).toBeFalse();
  });

  it('considera o limite UTF-8 sem normalizar ou truncar a senha', () => {
    const policy: PasswordPolicy = {
      minCodePoints: 0,
      maxCharacters: 72,
      maxUtf8Bytes: 72,
      requiresUppercase: false,
      requiresLowercase: false,
      requiresDigit: false,
      rejectsBlank: true,
    };

    expect(passwordRequirements('€'.repeat(25), policy).find((requirement) => requirement.key === 'bytes')?.satisfied).toBeFalse();
    expect(passwordRequirements('€'.repeat(25), { ...policy, maxUtf8Bytes: null }).find((requirement) => requirement.key === 'bytes')).toBeUndefined();
  });

  it('mostra erro estável para senha atual incorreta sem tratar como sessão expirada', () => {
    const error = new HttpErrorResponse({ status: 400, error: { code: 'current-password-incorrect' } });

    expect(describeProfileError(error)).toBe('A senha atual está incorreta.');
  });
});
