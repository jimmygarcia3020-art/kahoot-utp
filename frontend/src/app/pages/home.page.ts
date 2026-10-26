import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { SessionService } from '../core/session.service';
import { CatalogService } from '../core/catalog.service';
import { CatalogWarningComponent } from '../shared/notices.component';
import { CourseCardsComponent } from '../shared/course-cards.component';
import { EmptyComponent } from '../shared/empty.component';
import { badgeInfo } from '../core/util';

@Component({
  selector: 'app-home',
  imports: [RouterLink, IconDirective, CatalogWarningComponent, CourseCardsComponent, EmptyComponent],
  template: `
    <section class="page-heading">
      <div>
        <div class="eyebrow">UN POCO DE PRÁCTICA, MUCHAS POSIBILIDADES</div>
        <h1>{{ firstName() ? '¡Hola, ' + firstName() + '!' : '¡Qué bueno verte por aquí!' }} <span class="greeting-star">✳</span></h1>
        <p>Es un buen día para aprender algo nuevo. Vamos a tu ritmo.</p>
      </div>
      <span class="date-label">{{ today }}</span>
    </section>

    <section class="hero">
      <div class="hero-copy">
        <span class="hero-tag"><span></span> TU CÓDIGO. TU CAMINO.</span>
        <h2>Pequeños retos.<br><em>Grandes aprendizajes.</em></h2>
        <p>Practica, descubre por qué y vuelve a intentarlo.<br>Aquí, comprender es lo que más cuenta.</p>
        <a class="button light" routerLink="/retos">Explorar micro-retos <svg appIcon="arrow"></svg></a>
        <div class="hero-footnote"><svg appIcon="check"></svg> Sin presión de tiempo <svg appIcon="check"></svg> A tu propio ritmo</div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <span class="orbit orbit-one"></span><span class="orbit orbit-two"></span>
        <span class="floating-symbol symbol-one">{{ '{ }' }}</span><span class="floating-symbol symbol-two">+</span>
        <div class="code-window">
          <div class="code-window-bar"><i></i><i></i><i></i><span>mi_aprendizaje.py</span></div>
          <div class="code-lines">
            <span class="line-no">01</span><span><b>while</b> aprendiendo:</span>
            <span class="line-no">02</span><span>  intentar<span class="code-blue">()</span></span>
            <span class="line-no">03</span><span>  <b>if</b> hay_error:</span>
            <span class="line-no">04</span><span>    comprender<span class="code-blue">()</span></span>
            <span class="line-no">05</span><span>  <b>else</b>:</span>
            <span class="line-no">06</span><span>    seguir_creciendo<span class="code-blue">()</span></span>
          </div>
          <div class="code-terminal"><span>❯</span> Cada intento es un avance<span class="cursor"></span></div>
        </div>
        <div class="success-sticker"><svg appIcon="check"></svg> ¡Vas por buen camino!</div>
        <span class="tiny-star">✳</span>
      </div>
    </section>

    <section class="stats-grid" aria-label="Resumen de tu aprendizaje">
      <article class="stat-card"><span class="stat-icon mint"><svg appIcon="check"></svg></span><div><span>Retos completados</span><strong>{{ session.session() ? doneCount() : '—' }}<small></small></strong><p>{{ session.session() ? 'Cada uno es un paso adelante' : 'Inicia sesión para ver tu avance' }}</p></div></article>
      <article class="stat-card"><span class="stat-icon blue"><svg appIcon="spark"></svg></span><div><span>Puntos de dominio</span><strong>{{ session.session() ? pointsCount() : '—' }}<small> pts</small></strong><p>Comprender tiene su recompensa</p></div></article>
      <article class="stat-card"><span class="stat-icon peach"><svg appIcon="award"></svg></span><div><span>Insignias obtenidas</span><strong>{{ session.session() ? session.badges().length : '—' }}<small></small></strong><p>Pequeñas victorias, grandes motivos</p></div></article>
    </section>

    <app-catalog-warning />

    <div class="dashboard-columns">
      <section>
        <div class="section-heading">
          <div><h2>Tu próxima gran idea empieza aquí</h2><p>Tres caminos para poner en práctica lo que sabes.</p></div>
          <a class="text-link" routerLink="/cursos">Ver cursos <svg appIcon="arrow"></svg></a>
        </div>
        <div class="courses-grid">
          <app-course-cards />
          @if (!catalog.courses().length) {
            <app-empty title="Pronto habrá más por descubrir" description="Los cursos aparecerán aquí cuando el catálogo esté disponible." />
          }
        </div>
      </section>

      <aside class="home-aside">
        @if (session.isTeacher()) {
          <section class="join-card"><span class="tag"><svg appIcon="users"></svg> TU PRÓXIMA CLASE</span><h3>Ideas que se comparten.</h3><p>Prepara un quiz, crea una sala e invita a tu clase a aprender.</p><a class="button dark" routerLink="/docente/salas">Gestionar mis salas <svg appIcon="arrow"></svg></a><a class="text-link" routerLink="/docente/quizzes">Preparar un quiz →</a></section>
        } @else if (session.session()?.rol === 'USUARIO_EXTERNO') {
          <section class="join-card"><span class="tag"><svg appIcon="compass"></svg> APRENDE A TU RITMO</span><h3>Tu curiosidad marca el camino.</h3><p>Explora los tres cursos y practica sin límite de intentos.</p><a class="button dark" routerLink="/retos">Elegir mi próximo reto <svg appIcon="arrow"></svg></a></section>
        } @else {
          <section class="join-card"><span class="tag"><svg appIcon="users"></svg> APRENDE EN EQUIPO</span><h3>¿Tu clase ya empezó?</h3><p>Ingresa el PIN de tu docente y comparte el reto.</p>
            <form id="quick-pin" (submit)="joinPin($event)">
              <label class="sr-only" for="quick-pin-input">PIN de la sala</label>
              <input id="quick-pin-input" name="pin" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" placeholder="PIN de 6 dígitos" required>
              <button class="button dark" type="submit">Unirme a una sala <svg appIcon="arrow"></svg></button>
            </form>
            <span class="join-footnote"><svg appIcon="shield"></svg> Para estudiantes UTP</span></section>
        }

        <section class="milestone-card">
          <div class="row"><h3>{{ session.badges().length ? 'Tu último logro' : 'Tu siguiente logro' }}</h3><svg appIcon="spark"></svg></div>
          <div class="milestone-icon"><svg [appIcon]="lastBadge()?.[2] || 'flag'"></svg></div>
          <strong>{{ lastBadge()?.[0] || 'El primer paso' }}</strong>
          <p>{{ session.badges().length ? 'Ya es parte de tu historia. Sigue explorando.' : 'Todo empieza con un intento. Completa tu primer micro-reto.' }}</p>
          <a class="text-link" routerLink="/insignias">Conoce las insignias <svg appIcon="arrow"></svg></a>
        </section>
      </aside>
    </div>

    <section class="encouragement"><svg appIcon="bulb"></svg><p><strong>No tienes que saberlo todo para empezar.</strong> Solo necesitas curiosidad y ganas de intentarlo.</p><span>Un reto a la vez.</span></section>
  `,
})
export class HomePage {
  readonly session = inject(SessionService);
  readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);
  readonly today = new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'America/Lima' }).format(new Date());

  readonly firstName = computed(() => this.session.session()?.nombres.split(' ')[0] ?? '');
  readonly doneCount = computed(() => this.session.progress().filter(p => p.completado).length);
  readonly pointsCount = computed(() => this.session.progress().filter(p => p.puntoDominio).length);
  readonly lastBadge = computed(() => {
    const badges = this.session.badges();
    return badges.length ? badgeInfo[badges[badges.length - 1].tipo] : null;
  });

  joinPin(event: SubmitEvent): void {
    event.preventDefault();
    const pin = new FormData(event.target as HTMLFormElement).get('pin');
    this.router.navigateByUrl(`/salas?pin=${pin}`);
  }
}
