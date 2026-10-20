export type AdminRole = 'ADMIN' | 'USER';

export interface CompanyRef {
  id: string;
  name: string;
  cnpj: string;
}

export interface AdminSession {
  id: string;
  name: string;
  email: string;
  role: AdminRole;
}

export interface AdminUser extends AdminSession {
  enabled: boolean;
  companies: CompanyRef[];
  createdAt?: string;
}

export interface AdminUserRequest {
  name: string;
  email: string;
  role: AdminRole;
  companyIds: string[];
  password?: string;
}

export interface SetupStatus {
  required: boolean;
  enabled: boolean;
}

export interface SetupRequest {
  name: string;
  email: string;
  password: string;
}
