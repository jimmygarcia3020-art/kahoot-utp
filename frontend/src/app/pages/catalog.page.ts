import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { IconDirective } from '../core/icon.directive';
import { CatalogService } from '../core/catalog.service';
import { SessionService } from '../core/session.service';
import { CatalogWarningComponent } from '../shared/notices.component';
import { CourseCardsComponent } from '../shared/course-cards.component';
import { EmptyComponent } from '../shared/empty.component';
import { courseStyle, labels } from '../core/util';

@Component({
  selector: 'app-courses',
  imports: [CourseCardsComponent, CatalogWarningComponent, EmptyComponent],
  template: `
    <div class="page-heading"><div><div class="eyebrow">ELIGE TU CAMINO</div><h1>Mis cursos</h1><p>Construye tu conocimiento, un concepto a la vez.</p></div></div>
    <app-catalog-warning />
    <div class="courses-grid full-courses">
      <app-course-cards />
      @if (!catalog.courses().length) {
        <app-empty title="El catálogo está vacío" description="Aún no hay cursos disponibles." />
      }
    </div>
  `,
})
export class CoursesPage {
  readonly catalog = inject(CatalogService);
}

@Component({
  selector: 'app-catalog',
  imports: [RouterLink, IconDirective, CatalogWarningComponent, EmptyComponent],
  template: `
    <div class="page-heading">
      <div><div class="eyebrow">PRACTICAR ES DESCUBRIR</div><h1>Un reto para cada idea.</h1><p>Elige qué quieres aprender. Los intentos son ilimitados.</p></div>
      <span class="pill">{{ catalog.retos().length }} micro-retos disponibles</span>
    </div>
    <app-catalog-warning />
    <form id="filters" class="filter-bar" (submit)="applyFilters($event, false)">
      <label>Curso<select name="curso" (change)="applyFilters($event, true)">
        <option value="">Todos los cursos</option>
        @for (c of catalog.courses(); track c.id) { <option [value]="c.id" [selected]="String(c.id) === course()">{{ c.nombre }}</option> }
      </select></label>
      <label>Tema<select name="tema">
        <option value="">Todos los temas</option>
        @for (t of temasDisponibles(); track t.id) { <option [value]="t.id" [selected]="String(t.id) === tema()">{{ t.nombre }}</option> }
      </select></label>
      <label>Nivel<select name="nivel">
        <option value="">Todos los niveles</option>
        @for (l of levels; track l) { <option [value]="l" [selected]="l === nivel()">{{ label(l) }}</option> }
      </select></label>
      <label class="search-field">Buscar un reto<div><svg appIcon="search"></svg><input type="search" name="q" [value]="search()" placeholder="¿Qué quieres practicar?"></div></label>
      <button class="button dark" type="submit">Filtrar</button>
    </form>

    <div class="section-heading"><h2>{{ filtered().length }} {{ filtered().length === 1 ? 'reto para explorar' : 'retos para explorar' }}</h2><a class="text-link" routerLink="/retos">Limpiar filtros</a></div>
    <div class="challenge-grid">
      @for (r of filtered(); track r.id) {
        <article class="challenge-card">
          <div class="row">
            <span class="stat-icon {{ styleOf(r).key }}"><svg [appIcon]="styleOf(r).icon"></svg></span>
            <span class="pill {{ progressOf(r.id)?.completado ? 'completed' : '' }}">{{ progressOf(r.id)?.completado ? '✓ Completado' : label(r.nivel) }}</span>
          </div>
          <span class="overline">{{ courseOf(r)?.nombre }} · {{ catalog.temaOf(r.temaId)?.nombre }}</span>
          <h3>{{ r.enunciado }}</h3>
          <span class="challenge-type"><svg appIcon="code"></svg> {{ label(r.tipo) }}</span>
          <a class="course-link" [routerLink]="['/reto', r.id]">{{ progressOf(r.id)?.completado ? 'Volver a practicar' : 'Practicar ahora' }} <svg appIcon="arrow"></svg></a>
        </article>
      } @empty {
        <app-empty title="No encontramos retos con esos filtros" description="Prueba con otro tema, nivel o palabra." />
      }
    </div>
  `,
})
export class CatalogPage {
  readonly catalog = inject(CatalogService);
  readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly query = toSignal(inject(ActivatedRoute).queryParamMap, { requireSync: true });

  readonly String = String;
  readonly levels = ['BASICO', 'INTERMEDIO', 'AVANZADO'];
  readonly course = computed(() => this.query().get('curso') || '');
  readonly tema = computed(() => this.query().get('tema') || '');
  readonly nivel = computed(() => this.query().get('nivel') || '');
  readonly search = computed(() => this.query().get('q') || '');

  readonly temasDisponibles = computed(() => this.catalog.temas().filter(t => !this.course() || String(t.cursoId) === this.course()));

  readonly filtered = computed(() => {
    const term = this.search().toLocaleLowerCase('es');
    return this.catalog.retos().filter(r =>
      (!this.course() || String(r.cursoId) === this.course()) &&
      (!this.tema() || String(r.temaId) === this.tema()) &&
      (!this.nivel() || r.nivel === this.nivel()) &&
      (!term || r.enunciado.toLocaleLowerCase('es').includes(term)));
  });

  label(v: string): string { return labels[v] ?? v; }
  courseOf(r: any): any { return this.catalog.courseOf(r.cursoId); }
  styleOf(r: any) { return courseStyle(this.courseOf(r)?.nombre); }
  progressOf(id: number): any { return this.session.progress().find(p => p.microRetoId === id); }

  applyFilters(event: Event, clearTema: boolean): void {
    event.preventDefault();
    const form = (event.target as HTMLElement).closest('form') as HTMLFormElement;
    const data = new FormData(form);
    if (clearTema) data.set('tema', '');
    const params = Object.fromEntries(data) as Record<string, string>;
    this.router.navigate(['/retos'], { queryParams: params });
  }
}
