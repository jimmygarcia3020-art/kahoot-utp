import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { SessionService } from '../core/session.service';
import { CatalogService } from '../core/catalog.service';
import { CourseCardsComponent } from '../shared/course-cards.component';
import { AuthPromptComponent } from '../shared/notices.component';
import { EmptyComponent } from '../shared/empty.component';
import { badgeInfo } from '../core/util';

@Component({
  selector: 'app-progress',
  imports: [RouterLink, IconDirective, CourseCardsComponent, AuthPromptComponent, EmptyComponent],
  template: `
    @if (!session.session()) {
      <app-auth-prompt title="Tu aprendizaje merece su propio espacio." text="Inicia sesión para ver tus retos completados y puntos de dominio." />
    } @else {
      <div class="page-heading">
        <div><div class="eyebrow">MIRA TODO LO QUE ESTÁS CONSTRUYENDO</div><h1>Mi progreso</h1><p>Tu avance se mide en lo que comprendes, un reto a la vez.</p></div>
        <a class="button dark" routerLink="/retos">Seguir practicando <svg appIcon="arrow"></svg></a>
      </div>
      <div class="courses-grid full-courses"><app-course-cards /></div>
      <div class="section-heading"><h2>Tu recorrido</h2><span>Un punto de dominio por reto resuelto sin solución</span></div>
      <div class="panel">
        @if (session.progress().length) {
          <div class="table-scroll"><table>
            <thead><tr><th>Micro-reto</th><th>Resultado</th><th>Dominio</th><th></th></tr></thead>
            <tbody>
              @for (p of session.progress(); track p.id) {
                <tr>
                  <td>{{ catalog.retoOf(p.microRetoId)?.enunciado || 'Micro-reto ' + p.microRetoId }}</td>
                  <td>{{ p.completado ? (p.resueltoConAyuda ? 'Completado con ayuda' : 'Completado') : 'En práctica' }}</td>
                  <td>{{ p.puntoDominio ? '✓ 1 punto' : '—' }}</td>
                  <td><a class="text-link" [routerLink]="['/reto', p.microRetoId]">Ver reto <svg appIcon="arrow"></svg></a></td>
                </tr>
              }
            </tbody>
          </table></div>
        } @else {
          <app-empty title="Tu historia está por empezar" description="Completa un micro-reto y verás tu progreso aquí." />
        }
      </div>
    }
  `,
})
export class ProgressPage {
  readonly session = inject(SessionService);
  readonly catalog = inject(CatalogService);
}

@Component({
  selector: 'app-badges',
  imports: [IconDirective, AuthPromptComponent],
  template: `
    <div class="page-heading">
      <div><div class="eyebrow">CADA AVANCE TIENE SU HISTORIA</div><h1>Pequeñas victorias.</h1><p>Insignias que celebran tu aprendizaje y tu perseverancia.</p></div>
      <span class="pill">{{ session.badges().length }} de 5 obtenidas</span>
    </div>
    @if (!session.session()) {
      <app-auth-prompt title="Tus logros te esperan." text="Inicia sesión para empezar a coleccionar tus insignias." />
    }
    <div class="badge-grid">
      @for (b of badges(); track b.key) {
        <article class="badge-card {{ b.obtained ? 'earned' : '' }}">
          <div class="badge-emblem"><svg [appIcon]="b.icon"></svg></div>
          <span class="pill">{{ b.obtained ? '✓ Obtenida' : 'Por descubrir' }}</span>
          <h2>{{ b.title }}</h2>
          <p>{{ b.description }}</p>
        </article>
      }
    </div>
    <p class="muted">Las insignias se conceden cuando el servidor confirma que cumpliste su condición.</p>
  `,
})
export class BadgesPage {
  readonly session = inject(SessionService);
  readonly badges = computed(() => Object.entries(badgeInfo).map(([key, [title, description, icon]]) => ({
    key, title, description, icon,
    obtained: this.session.badges().some(b => b.tipo === key),
  })));
}
