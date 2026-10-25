import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import ptBr from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { JwtHelperService } from '@auth0/angular-jwt';

import { authErrorInterceptor } from '@core/interceptors/auth-error.interceptor';
import { authTokenInterceptor } from '@core/interceptors/auth-token.interceptor';

import { routes } from './app.routes';

registerLocaleData(ptBr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      // Parâmetros de rota chegam como input() nos componentes de detalhe.
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),
    provideHttpClient(withInterceptors([authTokenInterceptor, authErrorInterceptor])),
    { provide: JwtHelperService, useFactory: () => new JwtHelperService() },
    { provide: LOCALE_ID, useValue: 'pt' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'BRL' },
  ],
};
