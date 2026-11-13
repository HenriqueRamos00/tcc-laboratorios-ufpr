import { PortalUser, Token } from './token';

export function clientToken(source: 'lactec' | 'backend-lab' = 'lactec', overrides: Record<string, unknown> = {}): string {
  const claims = {
    exp: Math.floor(Date.now() / 1000) + 900,
    ...(source === 'lactec'
      ? { roles: 'CLIENTE', authSource: 'lactec', serviceCode: 'portal-web' }
      : { role: 'CLIENTE' }),
    ...overrides,
  };
  const encode = (value: object) => btoa(JSON.stringify(value))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(claims)}.assinatura-de-teste`;
}

export function clientIdentity(source: 'lactec' | 'backend-lab' = 'lactec'): PortalUser {
  return {
    id: '42', name: 'Marina Costa', email: 'marina@example.com', role: source === 'lactec' ? 'CLIENTE' : 'Client',
    ...(source === 'lactec'
      ? { serviceCode: 'portal-web', authorizations: { companyCnpjs: ['04252011000110', '12ABC34501DE35'] } }
      : { companies: [] }),
  };
}

export function clientLoginResponse(source: 'lactec' | 'backend-lab' = 'lactec'): Token {
  return { Token: clientToken(source), tokenType: 'Bearer', expiresIn: 900, user: clientIdentity(source) };
}
