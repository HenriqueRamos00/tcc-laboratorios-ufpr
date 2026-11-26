export interface LoginRequest {
  email: string;
  password: string;
}

export interface Administrator {
  id: string;
  name: string;
  email: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: Administrator;
}
