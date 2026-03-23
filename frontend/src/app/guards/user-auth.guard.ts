import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserAuthService } from '../services/user-auth.service';
import { map } from 'rxjs';

export const userAuthGuard: CanActivateFn = () => {
  const userAuth = inject(UserAuthService);
  const router = inject(Router);

  if (!userAuth.token) {
    router.navigate(['/login'], { state: { returnUrl: '/my-tickets' } });
    return false;
  }

  return userAuth.verify().pipe(
    map(valid => {
      if (!valid) {
        router.navigate(['/login'], { state: { returnUrl: '/my-tickets' } });
      }
      return valid;
    })
  );
};
