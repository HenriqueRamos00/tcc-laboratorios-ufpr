import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import { Profile } from '@/app/model/profile';
import { ProfileService } from './profile.service';

describe('Serviço de perfil interno', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta o perfil interno na rota de autoatendimento', () => {
    service.getProfile().subscribe();
    const request = http.expectOne(`${API_URL}/auth/internal/profile`);

    expect(request.request.method).toBe('GET');
    request.flush({} as Profile);
  });

  it('atualiza apenas o nome na rota da própria conta', () => {
    const update = { name: 'Helena Souza' };
    service.updatePersonalData(update).subscribe();
    const request = http.expectOne(`${API_URL}/auth/internal/profile`);

    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(update);
    request.flush({} as Profile);
  });

  it('envia a senha atual e a nova sem alterar os valores informados', () => {
    service.changePassword('  senha atual  ', '  NovaSenha123  ').subscribe();
    const request = http.expectOne(`${API_URL}/auth/internal/profile/password`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ currentPassword: '  senha atual  ', newPassword: '  NovaSenha123  ' });
    request.flush(null);
  });
});
