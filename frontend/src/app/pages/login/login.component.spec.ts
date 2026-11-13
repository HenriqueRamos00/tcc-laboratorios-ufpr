import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { LoginService } from '@/app/services/login.service';
import { clientLoginResponse } from '@/app/model/client-session.spec-util';
import { Token } from '@/app/model/token';
import { UserRole } from '@core/store/user-role.store';
import { LoginComponent } from './login.component';

describe('LoginComponent, erros e cooldown', () => {
  const login = jasmine.createSpy('login');
  beforeEach(() => {
    login.calls.reset();
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: LoginService, useValue: { login } },
      { provide: UserRole, useValue: { dashboardPath: () => '/cliente' } },
    ] });
  });
  it('desabilita envio pelo Retry-After e não repete a chamada automaticamente', fakeAsync(() => {
    login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '2' }) })));
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    component.form.patchValue({ email: 'marina@example.com', password: 'antiga' });
    component.submit();
    fixture.detectChanges();
    expect(component.cooldownRemaining()).toBe(2);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeTrue();
    component.submit();
    expect(login).toHaveBeenCalledTimes(1);
    tick(3000);
    fixture.detectChanges();
    expect(component.cooldownRemaining()).toBe(0);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeFalse();
    expect(login).toHaveBeenCalledTimes(1);
    fixture.destroy();
  }));
  for (const [status, message] of [[401, 'Credenciais inválidas'], [400, 'Confira os dados'], [502, 'Não foi possível entrar']] as const)
    it(`apresenta mensagem para HTTP ${status} sem impor nova política à senha existente`, () => {
      login.and.returnValue(throwError(() => new HttpErrorResponse({ status })));
      const fixture = TestBed.createComponent(LoginComponent);
      fixture.componentInstance.form.patchValue({ email: 'marina@example.com', password: 'old' });
      fixture.componentInstance.submit();
      expect(login).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.errorMessage()).toContain(message);
      expect(fixture.componentInstance.submitting()).toBeFalse();
    });

  it('apresenta manter conectado marcado e encaminha a escolha desmarcada ao serviço', () => {
    login.and.returnValue(of(clientLoginResponse()));
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const checkbox = fixture.nativeElement.querySelector('mat-checkbox input') as HTMLInputElement;
    expect(checkbox.checked).toBeTrue();
    checkbox.click();
    fixture.componentInstance.form.patchValue({ email: 'marina@example.com', password: 'antiga' });
    fixture.componentInstance.submit();
    expect(login).toHaveBeenCalledOnceWith({ email: 'marina@example.com', password: 'antiga' }, false);
    expect(navigate).toHaveBeenCalledWith('/cliente');
    fixture.destroy();
  });

  it('não envia formulário inválido e identifica os campos obrigatórios', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.submit();
    fixture.detectChanges();
    expect(login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#login-email-error').textContent).toContain('Campo obrigatório');
    expect(fixture.nativeElement.querySelector('#login-password-error').textContent).toContain('Campo obrigatório');
    fixture.destroy();
  });

  it('impede envio duplicado e cancela a chamada ao destruir a tela', () => {
    const pending = new Subject<Token>();
    login.and.returnValue(pending);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.form.patchValue({ email: 'marina@example.com', password: 'antiga' });
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();
    expect(login).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.submitting()).toBeTrue();
    fixture.destroy();
    expect(pending.observed).toBeFalse();
  });
});
