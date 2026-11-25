import { HttpErrorResponse } from '@angular/common/http';

import { PasswordPolicy, Profile, ProfileAction, ProfileErrorPayload } from './profile';

const ROLE_LABELS: Readonly<Record<string, string>> = {
  ADMIN: 'Administrador',
  ADMINISTRADOR: 'Administrador',
  TECNICO: 'Técnico',
};

const OPERATION_MESSAGES: Readonly<Record<string, string>> = {
  'current-password-incorrect': 'A senha atual está incorreta.',
  'invalid-email-change-token': 'Este link está inválido, vencido ou já foi utilizado.',
  'email-already-in-use': 'Este e-mail já está em uso. Informe outro endereço.',
  'email-change-conflict': 'A solicitação de alteração de e-mail não pode ser concluída. Atualize o perfil e tente novamente.',
  'invalid-profile': 'Revise os dados informados e tente novamente.',
  'invalid-password': 'A nova senha não atende à política da conta.',
  'rate-limit-exceeded': 'Muitas solicitações foram feitas. Aguarde antes de tentar novamente.',
  'profile-source-unavailable': 'O serviço de conta está indisponível no momento. Tente novamente.',
  forbidden: 'Sua conta não tem permissão para acessar esta operação.',
  'session-expired': 'Sua sessão expirou. Entre novamente para continuar.',
};

export interface PasswordRequirement {
  readonly key: string;
  readonly label: string;
  readonly satisfied: boolean;
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role.toUpperCase()] ?? role;
}

export function hasProfileAction(profile: Profile, action: ProfileAction): boolean {
  return profile.actions.includes(action);
}

export function passwordRequirements(
  password: string,
  policy: PasswordPolicy,
): readonly PasswordRequirement[] {
  const characters = Array.from(password);
  const byteLength = new TextEncoder().encode(password).length;
  const requirements: PasswordRequirement[] = [];

  if (policy.rejectsBlank) {
    requirements.push({
      key: 'nonblank',
      label: 'Não pode conter apenas espaços em branco',
      satisfied: /\S/.test(password),
    });
  }
  if (policy.minCodePoints > 0) {
    requirements.push({ key: 'minimum', label: `Pelo menos ${policy.minCodePoints} caracteres`, satisfied: characters.length >= policy.minCodePoints });
  }
  if (policy.maxCharacters !== null) {
    requirements.push({ key: 'maximum', label: 'Use uma senha mais curta.', satisfied: password.length <= policy.maxCharacters });
  }
  if (policy.maxUtf8Bytes !== null) {
    requirements.push({ key: 'bytes', label: 'Use uma senha mais curta.', satisfied: byteLength <= policy.maxUtf8Bytes });
  }
  if (policy.requiresUppercase) {
    requirements.push({ key: 'uppercase', label: 'Ao menos uma letra maiúscula', satisfied: /\p{Uppercase}/u.test(password) });
  }
  if (policy.requiresLowercase) {
    requirements.push({ key: 'lowercase', label: 'Ao menos uma letra minúscula', satisfied: /\p{Lowercase}/u.test(password) });
  }
  if (policy.requiresDigit) {
    requirements.push({ key: 'digit', label: 'Ao menos um número', satisfied: /\p{Nd}/u.test(password) });
  }
  return requirements;
}

export function describeProfileError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const payload = error.error as ProfileErrorPayload | null;
    const code = typeof payload?.code === 'string' ? payload.code : '';
    if (OPERATION_MESSAGES[code]) return OPERATION_MESSAGES[code];
    if (error.status === 401) return OPERATION_MESSAGES['session-expired'];
    if (error.status === 403) return OPERATION_MESSAGES['forbidden'];
    if (error.status === 429) return OPERATION_MESSAGES['rate-limit-exceeded'];
    if (error.status >= 500) return OPERATION_MESSAGES['profile-source-unavailable'];
  }
  return 'Não foi possível concluir a operação. Tente novamente.';
}

export function isErrorCode(error: unknown, code: string): boolean {
  if (!(error instanceof HttpErrorResponse)) return false;
  const payload = error.error as ProfileErrorPayload | null;
  return payload?.code === code;
}

export function emailExpiryLabel(expiresAt: string): string {
  const date = new Date(expiresAt);
  if (!Number.isFinite(date.getTime())) return 'A validade da solicitação não está disponível.';
  return `Os links expiram em ${new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date)}.`;
}
