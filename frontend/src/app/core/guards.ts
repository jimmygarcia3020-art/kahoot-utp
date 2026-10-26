import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';
import { ToastService } from './ui.service';
import { needsProfile } from './util';

// Una cuenta sin género o fecha de nacimiento completa su perfil antes de continuar.
export const requireProfile: CanActivateFn = () => {
  const session = inject(SessionService);
  return needsProfile(session.session()) ? inject(Router).createUrlTree(['/completar-perfil']) : true;
};

// Las rutas de docente exigen sesión; si no la hay, se ofrece iniciarla.
export const requireSession: CanActivateFn = () => {
  const session = inject(SessionService);
  if (session.session()) return true;
  inject(ToastService).show('Inicia sesión para guardar tu aprendizaje.');
  return inject(Router).createUrlTree(['/acceso']);
};
