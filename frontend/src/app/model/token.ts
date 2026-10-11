export interface UserCompany {
  id: string;
  name: string;
}

export interface AuthenticatedUser {
  id: string;
  name: string;
  email: string;
  role: 'Client';
  companies: UserCompany[];
}

export interface Token {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthenticatedUser;
}
