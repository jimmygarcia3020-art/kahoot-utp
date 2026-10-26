import { Component, HostListener, OnInit, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, NavigationStart, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IconDirective } from './core/icon.directive';
import { CatalogService } from './core/catalog.service';
import { SessionService } from './core/session.service';
import { ModalService, ToastService } from './core/ui.service';
import { PreferencesService } from './core/preferences.service';
import { HelpModal, ProfileModal } from './modals/modals';
import { ModalHostComponent } from './shared/modal-host.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, IconDirective, ModalHostComponent],
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly catalog = inject(CatalogService);
  readonly session = inject(SessionService);
  readonly toast = inject(ToastService);
  readonly modal = inject(ModalService);
  readonly preferences = inject(PreferencesService);
  private readonly router = inject(Router);

  readonly ready = signal(false);
  readonly menuOpen = signal(false);
  readonly url = signal(this.router.url);
  readonly path = computed(() => this.url().split('?')[0]);
  readonly demoMode = this.session.demoMode;
  readonly year = new Date().getFullYear();

  readonly nav = computed(() => {
    const items: [string, string, string][] = [['/', 'home', 'Mi inicio'], ['/cursos', 'book', 'Cursos'], ['/retos', 'code', 'Micro-retos'], ['/progreso', 'chart', 'Mi progreso'], ['/insignias', 'award', 'Mis insignias']];
    if (this.session.isTeacher()) items.push(['/docente/preguntas', 'edit', 'Mis preguntas'], ['/docente/quizzes', 'book', 'Mis quizzes'], ['/docente/salas', 'users', 'Mis salas']);
    else if (this.session.session()?.rol !== 'USUARIO_EXTERNO') items.push(['/salas', 'users', 'Salas en vivo']);
    return items;
  });

  readonly selected = computed(() => {
    const p = this.path();
    return p.startsWith('/reto/') ? '/retos' : p.startsWith('/docente/preguntas/') ? '/docente/preguntas' : p.startsWith('/docente/salas/') ? '/docente/salas' : p;
  });

  readonly pageLabel = computed(() => {
    const p = this.path();
    return this.nav().find(n => n[0] === this.selected())?.[2] || (p === '/ajustes' ? 'Accesibilidad' : p === '/acceso' ? 'Bienvenido' : 'Práctica');
  });

  readonly userName = computed(() => this.session.session()?.nombres || 'Visitante');
  readonly userRole = computed(() => {
    const s = this.session.session();
    return s ? (({ ESTUDIANTE_UTP: 'Estudiante UTP', DOCENTE_UTP: 'Docente UTP', USUARIO_EXTERNO: 'Autodidacta' } as Record<string, string>)[s.rol] ?? '') : 'Explora y aprende';
  });

  constructor() {
    this.router.events.pipe(takeUntilDestroyed()).subscribe(event => {
      if (event instanceof NavigationStart) { this.modal.close(); this.menuOpen.set(false); }
      if (event instanceof NavigationEnd) this.url.set(event.urlAfterRedirects);
    });
    effect(() => {
      const home = this.path() === '/';
      document.title = `AdaptCode UTP · ${home ? 'Mi inicio' : 'Tu espacio de aprendizaje'}`;
    });
    matchMedia('(min-width: 761px)').addEventListener('change', e => { if (e.matches) this.menuOpen.set(false); });
  }

  async ngOnInit(): Promise<void> {
    await Promise.all([
      this.catalog.load(),
      this.session.loadPersonal().catch(e => this.toast.show((e as Error).message || 'No se pudo cargar tu espacio.')),
    ]);
    this.ready.set(true);
  }

  focusMain(event: Event): void {
    event.preventDefault();
    document.querySelector<HTMLElement>('#main')?.focus();
  }

  toggleMenu(): void { this.menuOpen.update(v => !v); }

  closeMenu(): void { this.menuOpen.set(false); }

  openHelp(): void { this.modal.open('Cada intento cuenta', HelpModal); }

  openProfile(): void { this.modal.open('Tu espacio', ProfileModal); }

  onProfileButton(): void {
    if (this.session.session()) this.openProfile();
    else this.router.navigateByUrl('/acceso');
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.menuOpen()) this.closeMenu(); }

  readonly mainInert = computed(() => this.menuOpen());
}
