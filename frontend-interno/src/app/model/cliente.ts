import { EmpresaStatus } from './empresa';

export type ClienteStatus = 'ACTIVE' | 'INACTIVE';
export type ClienteStatusFilter = ClienteStatus | 'ALL';

export interface ClienteEmpresa {
  id: string;
  name: string;
  status?: EmpresaStatus;
}

export interface Cliente {
  id: string;
  name: string;
  email: string;
  phone: string;
  position: string;
  status: ClienteStatus;
  companies: ClienteEmpresa[];
}

export interface ClienteCreateRequest {
  name: string;
  email: string;
  phone: string;
  position: string;
  status: ClienteStatus;
  companyIds: string[];
  password: string;
}

export interface ClienteUpdateRequest extends Omit<ClienteCreateRequest, 'password'> {
  password?: string;
}
