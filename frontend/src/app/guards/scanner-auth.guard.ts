import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map } from 'rxjs';

export const scannerAuthGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.token) {
    router.navigate(['/scan/login']);
    return false;
  }

  return auth.verify().pipe(
    map(valid => {
      if (!valid) {
        router.navigate(['/scan/login']);
      }
      return valid;
    })
  );
};
