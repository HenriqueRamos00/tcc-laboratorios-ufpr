declare global {
  interface Window {
    __CLIENT_AUTH_API_URL__?: string;
    __CLIENT_DEMO_DATA__?: boolean;
  }
}

const configuredAuthUrl = typeof window === 'undefined' ? undefined : window.__CLIENT_AUTH_API_URL__;

export const API_URL = '/api';
export const DEMO_DATA_ENABLED = typeof window !== 'undefined' && window.__CLIENT_DEMO_DATA__ === true;
export const AUTH_API_URL = (configuredAuthUrl?.trim() || API_URL).replace(/\/+$/, '');
