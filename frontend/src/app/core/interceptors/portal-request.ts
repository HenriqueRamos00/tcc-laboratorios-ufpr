import { API_URL } from '@/app/environment/env';

export function isPortalProtectedRequest(url: string): boolean {
  try {
    const browserOrigin = typeof location === 'undefined' ? 'http://localhost' : location.origin;
    const api = new URL(API_URL, browserOrigin);
    const target = new URL(url, browserOrigin);
    const prefix = api.pathname.replace(/\/$/, '');
    return target.origin === api.origin && (
      target.pathname === `${prefix}/auth/me` || target.pathname === `${prefix}/api` ||
      target.pathname.startsWith(`${prefix}/api/`)
    );
  } catch {
    return false;
  }
}
