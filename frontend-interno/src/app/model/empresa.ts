export type EmpresaStatus = 'ACTIVE' | 'INACTIVE';
export type EmpresaStatusFilter = EmpresaStatus | 'ALL';

export interface Empresa {
  id: string;
  legalName: string;
  cnpj: string;
  address: string;
  city: string;
  state: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  status: EmpresaStatus;
  origin: 'LOCAL';
}

export interface EmpresaRequest {
  legalName: string;
  cnpj: string;
  address: string;
  city: string;
  state: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  status: EmpresaStatus;
}
