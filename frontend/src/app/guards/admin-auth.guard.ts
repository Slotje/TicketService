import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminAuthService } from '../services/admin-auth.service';
import { map } from 'rxjs';

export const adminAuthGuard: CanActivateFn = () => {
  const adminAuth = inject(AdminAuthService);
  const router = inject(Router);

  if (!adminAuth.token) {
    router.navigate(['/admin/login']);
    return false;
  }

  return adminAuth.verify().pipe(
    map(valid => {
      if (!valid) {
        router.navigate(['/admin/login']);
      }
      return valid;
    })
  );
};
