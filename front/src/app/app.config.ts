import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { routes } from './app.routes';
import { sessionInterceptor } from './session.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Rutas con hash: el back sirve un solo index.html sin reglas de reescritura.
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([sessionInterceptor])),
  ],
};
