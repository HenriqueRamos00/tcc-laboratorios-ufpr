export interface PortalUser {
  id: string;
  name: string;
  email: string;
  role: string;
  companies?: ReadonlyArray<{ id: string; name: string }>;
  serviceCode?: string;
  authorizations?: { companyCnpjs: string[] };
}

export interface Token {
  Token: string;
  tokenType: string;
  expiresIn: number;
  user: PortalUser;
}
