export type InternalRole = 'ADMIN' | 'TECNICO';

export interface InternalUser {
  id: number;
  name: string;
  email: string;
  role: InternalRole;
}

export interface InternalLoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: InternalUser;
}
