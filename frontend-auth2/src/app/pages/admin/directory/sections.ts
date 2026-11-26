import { Resource } from '@/app/model/access';

export interface DirectorySection {
  resource: Resource;
  path: string;
  label: string;
  icon: string;
  create: string;
  edit: string;
  columns: string[];
}

export const sections: DirectorySection[] = [
  {
    resource: 'services',
    path: 'aplicacoes',
    label: 'Aplicações',
    icon: 'apps',
    create: 'Nova aplicação',
    edit: 'Editar aplicação',
    columns: ['Código', 'Situação'],
  },
  {
    resource: 'companies',
    path: 'empresas',
    label: 'Empresas',
    icon: 'business',
    create: 'Nova empresa',
    edit: 'Editar empresa',
    columns: ['CNPJ'],
  },
  {
    resource: 'users',
    path: 'clientes',
    label: 'Clientes',
    icon: 'groups',
    create: 'Novo cliente',
    edit: 'Editar cliente',
    columns: ['Aplicação', 'Empresas', 'Acesso', 'E-mail'],
  },
  {
    resource: 'admins',
    path: 'administradores',
    label: 'Administradores',
    icon: 'admin_panel_settings',
    create: 'Novo administrador',
    edit: 'Editar administrador',
    columns: ['E-mail', 'Situação'],
  },
];

export const accessBreadcrumb = { label: 'Administração de acessos', route: '/admin' };

export function formatCnpj(value: string): string {
  return value.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
}
