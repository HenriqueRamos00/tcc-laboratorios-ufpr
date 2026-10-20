export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  enabled: boolean;
  createdAt?: string;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
}

export interface ApiKeyCreateRequest {
  name: string;
  expiresAt?: string | null;
}

export interface ApiKeyCreateResponse {
  key: string;
  apiKey: ApiKey;
}
