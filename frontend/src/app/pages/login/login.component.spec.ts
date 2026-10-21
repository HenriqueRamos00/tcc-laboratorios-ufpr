import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { LoginService } from '@/app/services/login.service';
import { LoginComponent } from './login.component';

describe('LoginComponent, erros e cooldown', () => {
  const login = jasmine.createSpy('login');
  beforeEach(() => {
    login.calls.reset();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: LoginService, useValue: { login } }] });
  });
  it('desabilita envio pelo Retry-After e não repete a chamada automaticamente', fakeAsync(() => {
    login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '2' }) })));
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    component.form.setValue({ email: 'marina@example.com', password: 'antiga' });
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
      fixture.componentInstance.form.setValue({ email: 'marina@example.com', password: 'old' });
      fixture.componentInstance.submit();
      expect(login).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.errorMessage()).toContain(message);
      expect(fixture.componentInstance.submitting()).toBeFalse();
    });
});
