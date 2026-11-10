import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/** Sesión vencida: el back responde 401 en /api y aquí se manda al login. */
export const sessionInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        window.location.href = '/login';
      }
      return throwError(() => err);
    }),
  );
