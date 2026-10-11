import { isDevMode } from '@angular/core';

import type { Token } from '@/app/model/token';

const DEV_TOKEN_LIFETIME_SECONDS = 60 * 60 * 24;

function base64UrlEncode(value: string): string {
  return btoa(value).replace(/=+$/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

// shortcut: login local da branch angelo/fet/autolab; remover quando o C# oferecer autenticação real.
export function tryDevLogin(email: string, password: string): Token | null {
  if (!isDevMode() || email.trim().toLowerCase() !== 'lactec@lactec' || password !== 'lactec') {
    return null;
  }

  const issuedAt = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = base64UrlEncode(JSON.stringify({
    nome: 'Cliente Lactec', roles: 'CLIENTE',
    iat: issuedAt, exp: issuedAt + DEV_TOKEN_LIFETIME_SECONDS,
  }));

  return {
    accessToken: `${header}.${body}.dev-signature`,
    tokenType: 'Bearer',
    expiresIn: DEV_TOKEN_LIFETIME_SECONDS,
    user: {
      id: 'dev-lactec', name: 'Cliente Lactec', email: 'lactec@lactec',
      role: 'Client', companies: [],
    },
  };
}
