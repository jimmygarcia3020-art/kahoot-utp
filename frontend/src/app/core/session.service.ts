import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';
import { ToastService } from './ui.service';
import { PreferencesService } from './preferences.service';
import { AuthSession, needsProfile, readStored, validSession } from './util';
import { createDemo } from './demo';

// Sesión JWT en sessionStorage, con expiración, y progreso/insignias del usuario.
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly preferences = inject(PreferencesService);

  readonly demoMode = new URLSearchParams(location.search).get('demo') === '1';
  private readonly sessionKey = this.demoMode ? 'adaptcode.demo.session' : 'adaptcode.session';

  readonly session = signal<AuthSession | null>(validSession(readStored<AuthSession>(sessionStorage, this.sessionKey)));
  readonly progress = signal<any[]>([]);
  readonly badges = signal<any[]>([]);
  readonly isTeacher = computed(() => this.session()?.rol === 'DOCENTE_UTP');
  private personalLoaded = false;

  constructor() {
    if (this.demoMode) this.api.useDemo(createDemo());
    this.api.configure({
      token: () => this.session()?.token ?? null,
      onUnauthorized: () => {
        this.clear();
        this.toast.show('Tu sesión terminó. Inicia sesión para continuar.');
        queueMicrotask(() => { if (!location.hash.startsWith('#/acceso')) this.router.navigateByUrl('/acceso'); });
      },
    });
  }

  userPath(resource: string): string { return `/usuarios/${this.session()!.usuarioId}/${resource}`; }
  teacherPath(resource: string): string { return `/docentes/${this.session()!.usuarioId}/${resource}`; }

  requireSession(): boolean {
    if (this.session()) return true;
    this.router.navigateByUrl('/acceso');
    this.toast.show('Inicia sesión para guardar tu aprendizaje.');
    return false;
  }

  clear(): void {
    this.session.set(null);
    sessionStorage.removeItem(this.sessionKey);
    this.progress.set([]);
    this.badges.set([]);
    this.personalLoaded = false;
  }

  async signIn(body: { correo: string; contrasena: string }): Promise<void> {
    const result = await this.api.request<AuthSession>('/auth/login', { method: 'POST', body });
    const session = { ...result, expiresAt: Date.now() + result.expiraEnMs };
    this.session.set(session);
    sessionStorage.setItem(this.sessionKey, JSON.stringify(session));
    this.progress.set([]); this.badges.set([]); this.personalLoaded = false;
    await this.loadPersonal();
    this.router.navigateByUrl(needsProfile(this.session()) ? '/completar-perfil' : '/');
  }

  signOut(): void {
    this.clear();
    this.router.navigateByUrl('/');
  }

  // Datos de perfil completado: se reflejan en la sesión guardada.
  saveProfile(genero: string, fechaNacimiento: string): void {
    const current = this.session();
    if (!current) return;
    const updated = { ...current, genero, fechaNacimiento };
    this.session.set(updated);
    sessionStorage.setItem(this.sessionKey, JSON.stringify(updated));
  }

  async loadPersonal(force = false): Promise<void> {
    if (!this.session() || (this.personalLoaded && !force)) return;
    const [progress, badges, preferences] = await Promise.all([
      this.api.request<any[]>(this.userPath('progreso')),
      this.api.request<any[]>(this.userPath('insignias')),
      this.api.request<any>(this.userPath('preferencias')).catch(e => { if (e.status === 404) return null; throw e; }),
    ]);
    this.progress.set(progress);
    this.badges.set(badges);
    if (preferences) this.preferences.set(preferences);
    this.personalLoaded = true;
  }
}
