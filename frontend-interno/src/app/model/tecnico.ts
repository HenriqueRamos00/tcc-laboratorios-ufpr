export type TecnicoStatus = 'ACTIVE' | 'INACTIVE';

export interface Tecnico {
  id: number;
  name: string;
  email: string;
  unit: string | null;
  specialty: string | null;
  status: TecnicoStatus;
}

export interface CreateTecnicoRequest {
  name: string;
  email: string;
  unit: string;
  specialty: string;
  status: TecnicoStatus;
  password: string;
}

export interface UpdateTecnicoRequest {
  name: string;
  email: string;
  unit: string;
  specialty: string;
  status: TecnicoStatus;
  password?: string;
}

export type TecnicoFilterStatus = TecnicoStatus | 'ALL';
