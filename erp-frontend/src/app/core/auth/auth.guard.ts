import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

const CHANGE_PASSWORD_URL = '/config/change-password';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  // Mot de passe à changer obligatoirement : bloquer toute autre page tant que ce n'est pas fait,
  // pour empêcher un contournement en naviguant directement vers une autre URL/module.
  if (authService.getSession()?.mustChangePassword && !state.url.startsWith(CHANGE_PASSWORD_URL)) {
    router.navigate([CHANGE_PASSWORD_URL]);
    return false;
  }

  return true;
};
