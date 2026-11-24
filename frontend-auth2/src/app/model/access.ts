import type { Administrator } from './login';

export interface Application {
  id: string;
  name: string;
  code: string;
  active: boolean;
}

export interface Company {
  id: string;
  name: string;
  cnpj: string;
}

export interface ClientAccount {
  id: string;
  name: string;
  email: string;
  active: boolean;
  role: 'CLIENTE';
  emailConfirmed: boolean;
  serviceId: string;
  companyIds: string[];
}

export interface AdminAccount extends Administrator {
  active: boolean;
}

export interface Records {
  services: Application;
  companies: Company;
  users: ClientAccount;
  admins: AdminAccount;
}

export type Resource = keyof Records;

export interface CreateRequests {
  services: Pick<Application, 'name' | 'code'>;
  companies: Pick<Company, 'name' | 'cnpj'>;
  users: Pick<ClientAccount, 'name' | 'email' | 'serviceId' | 'companyIds'> & { password: string };
  admins: Pick<AdminAccount, 'name' | 'email'> & { password: string };
}

export interface UpdateRequests {
  services: Omit<Application, 'id'>;
  companies: Omit<Company, 'id'>;
  users: Pick<ClientAccount, 'name' | 'active' | 'companyIds'>;
  admins: Omit<AdminAccount, 'id'> & { password?: string };
}

export interface ApplicationToken {
  id: string;
  serviceId: string;
  name: string;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
}

export interface IssuedToken {
  accessToken: string;
  token: Omit<ApplicationToken, 'revokedAt'>;
}
