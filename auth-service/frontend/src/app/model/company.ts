export interface Company {
  id: string;
  name: string;
  cnpj: string;
  userCount: number;
  createdAt?: string;
}

export interface CompanyRequest {
  name: string;
  cnpj: string;
}
