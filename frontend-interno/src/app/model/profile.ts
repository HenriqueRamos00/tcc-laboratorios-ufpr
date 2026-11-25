export type ProfileEditableField = 'name';
export type ProfileAction = 'change-email' | 'change-password';

export interface PasswordPolicy {
  readonly minCodePoints: number;
  readonly maxCharacters: number | null;
  readonly maxUtf8Bytes: number | null;
  readonly requiresUppercase: boolean;
  readonly requiresLowercase: boolean;
  readonly requiresDigit: boolean;
  readonly rejectsBlank: boolean;
}

export interface PendingEmailChange {
  readonly newEmail: string;
  readonly expiresAt: string;
  readonly currentEmailConfirmed: boolean;
  readonly newEmailConfirmed: boolean;
}

export interface Profile {
  readonly name: string;
  readonly email: string;
  readonly role: string;
  readonly editableFields: readonly ProfileEditableField[];
  readonly actions: readonly ProfileAction[];
  readonly passwordPolicy: PasswordPolicy;
  readonly active?: boolean;
  readonly unit?: string;
  readonly specialty?: string;
  readonly pendingEmailChange?: PendingEmailChange | null;
}

export interface ProfileUpdateRequest {
  readonly name?: string;
}

export interface EmailChangeConfirmationResponse {
  readonly status: 'awaiting-other-confirmation' | 'completed';
}

export interface ProfileErrorPayload {
  readonly code?: string;
  readonly errors?: Readonly<Record<string, readonly string[] | string>>;
}
