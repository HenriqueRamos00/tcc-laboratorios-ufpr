// DEV-ONLY: apagar este arquivo + remover o uso em login.service.ts
// (import e bloco `if (dev) {...}`) quando o backend de auth estiver pronto.
import { Token } from '@/app/model/token';

export interface DevAuthResult {
  token: Token;
  username: string;
  roleClaim: string;
}

interface DevUser {
  email: string;
  password: string;
  username: string;
  roleClaim: string;
}

const DEV_TOKEN_LIFETIME_SECONDS = 60 * 60 * 24;

const DEV_USERS: ReadonlyArray<DevUser> = [
  {
    email: 'lactec@lactec',
    password: 'lactec',
    username: 'Cliente Lactec',
    roleClaim: 'CLIENTE',
  },
];

function base64UrlEncode(value: string): string {
  return btoa(value).replace(/=+$/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function makeDevJwt(payload: Record<string, unknown>): string {
  const header = base64UrlEncode(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = base64UrlEncode(
    JSON.stringify({
      ...payload,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + DEV_TOKEN_LIFETIME_SECONDS,
    }),
  );
  return `${header}.${body}.dev-signature`;
}

export function tryDevLogin(email: string, password: string): DevAuthResult | null {
  const normalizedEmail = email.trim().toLowerCase();
  const match = DEV_USERS.find(
    (usuario) => usuario.email === normalizedEmail && usuario.password === password,
  );
  if (!match) return null;

  return {
    token: {
      Token: makeDevJwt({ nome: match.username, roles: match.roleClaim }),
    },
    username: match.username,
    roleClaim: match.roleClaim,
  };
}
