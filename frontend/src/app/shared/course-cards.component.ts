import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { CatalogService } from '../core/catalog.service';
import { SessionService } from '../core/session.service';
import { courseStyle, percent } from '../core/util';

// Tarjetas de los tres cursos con su progreso (se usan en Inicio, Mis cursos y Progreso).
@Component({
  selector: 'app-course-cards',
  imports: [RouterLink, IconDirective],
  template: `
    @for (vm of cards(); track vm.c.id) {
      <article class="course-card {{ vm.style.key }}">
        <div class="course-cover">
          <span class="course-number">RUTA 0{{ vm.index + 1 }}</span>
          <div class="course-art"><svg [appIcon]="vm.style.icon"></svg></div>
          <span class="course-cover-label">{{ vm.style.subtitle }}</span>
          <span class="art-grid" aria-hidden="true"></span>
        </div>
        <div class="course-body">
          <div class="row"><h3>{{ vm.c.nombre }}</h3><span class="course-mini-icon"><svg [appIcon]="vm.style.icon"></svg></span></div>
          <p>{{ vm.style.description }}</p>
          <div class="course-meta"><svg appIcon="book"></svg> {{ vm.temas }} {{ vm.temas === 1 ? 'tema' : 'temas' }} <span>·</span> {{ vm.retos }} {{ vm.retos === 1 ? 'micro-reto' : 'micro-retos' }}</div>
          <div class="row progress-label">
            <span>{{ session.session() ? vm.done + ' de ' + vm.retos + ' completados' : 'Tu próximo paso comienza aquí' }}</span>
            <strong>{{ session.session() ? vm.pct + '%' : '' }}</strong>
          </div>
          <progress [value]="vm.done" [max]="vm.retos || 1" [attr.aria-label]="'Progreso en ' + vm.c.nombre"></progress>
          <a class="course-link" [routerLink]="['/retos']" [queryParams]="{ curso: vm.c.id }">Explorar curso <svg appIcon="arrow"></svg></a>
        </div>
      </article>
    }
  `,
})
export class CourseCardsComponent {
  readonly catalog = inject(CatalogService);
  readonly session = inject(SessionService);

  readonly cards = computed(() => this.catalog.courses().map((c, index) => {
    const retos = this.catalog.retos().filter(r => r.cursoId === c.id);
    const done = this.session.progress().filter(p => p.completado && retos.some(r => r.id === p.microRetoId)).length;
    return {
      c, index,
      style: courseStyle(c.nombre),
      temas: this.catalog.temas().filter(t => t.cursoId === c.id).length,
      retos: retos.length,
      done,
      pct: percent(done, retos.length),
    };
  }));
}
