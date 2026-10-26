import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { CatalogService } from '../core/catalog.service';

// Aviso cuando el catálogo público no se pudo cargar.
@Component({
  selector: 'app-catalog-warning',
  imports: [IconDirective],
  template: `
    @if (catalog.error()) {
      <div class="notice warning" role="alert"><svg appIcon="alert"></svg>
        <div><strong>El catálogo no está disponible</strong><p>{{ catalog.error() }}</p>
          <button class="text-button" type="button" (click)="catalog.load(true)">Volver a intentar</button>
          <a href="/?demo=1#/">Explorar la demo</a></div>
      </div>
    }`,
})
export class CatalogWarningComponent {
  readonly catalog = inject(CatalogService);
}

// Invitación a iniciar sesión cuando la vista requiere cuenta.
@Component({
  selector: 'app-auth-prompt',
  imports: [RouterLink, IconDirective],
  template: `
    <div class="auth-prompt"><svg appIcon="shield"></svg>
      <div><h2>{{ title() }}</h2><p>{{ text() }}</p></div>
      <a class="button dark" routerLink="/acceso">Iniciar sesión <svg appIcon="arrow"></svg></a>
    </div>`,
})
export class AuthPromptComponent {
  readonly title = input.required<string>();
  readonly text = input.required<string>();
}
