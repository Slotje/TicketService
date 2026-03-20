import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { CustomerAuthService } from '../services/customer-auth.service';
import { map, take } from 'rxjs';

export const customerAuthGuard = () => {
  const customerAuth = inject(CustomerAuthService);
  const router = inject(Router);

  if (!customerAuth.token) {
    router.navigate(['/klant/login']);
    return false;
  }

  return customerAuth.verify().pipe(
    take(1),
    map(valid => {
      if (!valid) {
        router.navigate(['/klant/login']);
      }
      return valid;
    })
  );
};
