import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import { PendingEmailChange, Profile } from '@/app/model/profile';
import { ProfileService } from './profile.service';

describe('Serviço de perfil do cliente', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta o perfil na rota de autoatendimento', () => {
    service.getProfile().subscribe();
    const request = http.expectOne(`${API_URL}/api/profile`);

    expect(request.request.method).toBe('GET');
    request.flush({} as Profile);
  });

  it('envia os dados editados para a própria rota de perfil', () => {
    const update = { name: 'Camila Almeida', phone: '(41) 99999-1234', position: 'Responsável técnica' };
    service.updatePersonalData(update).subscribe();
    const request = http.expectOne(`${API_URL}/api/profile`);

    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(update);
    request.flush({} as Profile);
  });

  it('mantém senha e token no corpo das operações sensíveis', () => {
    const pending: PendingEmailChange = {
      newEmail: 'nova@example.com',
      expiresAt: '2026-11-23T12:00:00Z',
      currentEmailConfirmed: false,
      newEmailConfirmed: false,
    };
    service.requestEmailChange('nova@example.com', '  senha atual  ').subscribe();
    const emailRequest = http.expectOne(`${API_URL}/api/profile/email-change`);
    expect(emailRequest.request.method).toBe('POST');
    expect(emailRequest.request.body).toEqual({ newEmail: 'nova@example.com', currentPassword: '  senha atual  ' });
    emailRequest.flush(pending);

    service.confirmEmailChange('token-seguro').subscribe();
    const confirmation = http.expectOne(`${API_URL}/api/profile/email-change/confirm`);
    expect(confirmation.request.method).toBe('POST');
    expect(confirmation.request.body).toEqual({ token: 'token-seguro' });
    confirmation.flush({ status: 'awaiting-other-confirmation' });

    service.changePassword('  atual  ', '  NovaSenha123  ').subscribe();
    const passwordRequest = http.expectOne(`${API_URL}/api/profile/password`);
    expect(passwordRequest.request.method).toBe('POST');
    expect(passwordRequest.request.body).toEqual({ currentPassword: '  atual  ', newPassword: '  NovaSenha123  ' });
    passwordRequest.flush(null);
  });

  it('cancela somente a solicitação pendente da conta autenticada', () => {
    service.cancelEmailChange().subscribe();
    const request = http.expectOne(`${API_URL}/api/profile/email-change`);

    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
