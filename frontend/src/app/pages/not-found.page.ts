import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyComponent } from '../shared/empty.component';

@Component({
  selector: 'app-not-found',
  imports: [EmptyComponent, RouterLink],
  template: `
    <app-empty title="Este camino todavía no existe" description="Regresa al inicio y sigue explorando.">
      <a class="button dark" routerLink="/">Volver al inicio</a>
    </app-empty>`,
})
export class NotFoundPage {}
