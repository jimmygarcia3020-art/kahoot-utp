import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { ToastService } from '../core/ui.service';
import { formDataObject, maxBirthDate, needsProfile } from '../core/util';

type AuthMode = 'login' | 'registro' | 'verificar' | 'recuperar' | 'restablecer';

const titles: Record<AuthMode, [string, string]> = {
  login: ['¡Sigamos aprendiendo!', 'Tu siguiente descubrimiento está a un inicio de sesión.'],
  registro: ['Tu camino empieza aquí.', 'Crea tu cuenta y aprende a tu propio ritmo.'],
  verificar: ['Un paso más y listo.', 'Confirma tu cuenta con el código de seis dígitos.'],
  recuperar: ['Volvamos a tu espacio.', 'Solicita un código para restablecer tu contraseña.'],
  restablecer: ['Una nueva contraseña.', 'Usa el código de recuperación para continuar.'],
};
const buttons: Record<AuthMode, string> = {
  login: 'Iniciar sesión', registro: 'Crear mi cuenta', verificar: 'Verificar cuenta', recuperar: 'Solicitar código', restablecer: 'Guardar contraseña',
};
const routes: Record<string, string> = {
  registro: 'registro', verificar: 'verificar-cuenta', recuperar: 'recuperar-contrasena', restablecer: 'restablecer-contrasena',
};

@Component({
  selector: 'app-auth',
  imports: [RouterLink, IconDirective],
  template: `
    <div class="auth-layout">
      <section class="auth-story">
        <span class="hero-tag">APRENDE. COMPRENDE. CRECE.</span>
        <h1>Lo que hoy parece un reto,<br><em>mañana será parte de ti.</em></h1>
        <div class="auth-braces">{{ '{' }} <span>tú puedes</span> {{ '}' }}</div>
        <p>Algoritmos, Python y Bases de Datos.<br>Un espacio accesible para intentarlo las veces que necesites.</p>
        <a class="text-link" routerLink="/cursos">Explorar los cursos <svg appIcon="arrow"></svg></a>
      </section>
      <section class="auth-panel">
        <div class="eyebrow">BIENVENIDO A ADAPTCODE UTP</div>
        <h2>{{ titles()[0] }}</h2>
        <p>{{ titles()[1] }}</p>
        <form id="auth-form" class="stack-form" (submit)="onSubmit($event)">
          @if (mode() === 'registro') {
            <div class="form-grid">
              <label>Nombres<input name="nombres" autocomplete="given-name" maxlength="100" required></label>
              <label>Apellidos<input name="apellidos" autocomplete="family-name" maxlength="100" required></label>
            </div>
            <div class="form-grid">
              <label>Género<select name="genero" required>
                <option value="" disabled selected>Selecciona</option>
                <option value="MASCULINO">Masculino</option>
                <option value="FEMENINO">Femenino</option>
              </select></label>
              <label>Fecha de nacimiento<input type="date" name="fechaNacimiento" [attr.max]="maxDate" required></label>
            </div>
          }
          <label>Correo electrónico<input #correo type="email" name="correo" autocomplete="username" maxlength="254" [value]="data().correo ?? ''" placeholder="tu.correo@utp.edu.pe" required></label>
          @if (mode() === 'verificar' || mode() === 'restablecer') {
            <label>Código de verificación<input name="codigo" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" placeholder="6 dígitos" required></label>
          }
          @if (mode() !== 'recuperar' && mode() !== 'verificar') {
            <label>{{ mode() === 'restablecer' ? 'Nueva contraseña' : 'Contraseña' }}
              <div class="password-field">
                <input [type]="showPassword() ? 'text' : 'password'" [name]="mode() === 'restablecer' ? 'nuevaContrasena' : 'contrasena'" [attr.autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'" [attr.minlength]="mode() === 'login' ? null : 8" [attr.maxlength]="mode() === 'login' ? null : 72" required>
                <button class="text-button" type="button" id="toggle-password" [attr.aria-label]="'Mostrar contraseña'" (click)="showPassword.set(!showPassword())">{{ showPassword() ? 'Ocultar' : 'Mostrar' }}</button>
              </div>
            </label>
          }
          @if (mode() === 'registro') {
            <p class="field-hint">Estudiante: U + 8 dígitos. Docente: C + 5 dígitos. También puedes usar un correo personal para práctica autónoma.</p>
          }
          <div class="form-error" role="alert">{{ error() }}</div>
          <button type="submit" class="button dark" [disabled]="busy()">{{ buttons[mode()] }} <svg appIcon="arrow"></svg></button>
        </form>

        @if (data().codigo) {
          <div class="notice info"><div>Código recibido de la API para esta versión de desarrollo: <strong>{{ data().codigo }}</strong></div></div>
        }

        <div class="auth-links">
          @if (mode() === 'login') {
            <button class="text-button" type="button" (click)="show('registro')">Crear una cuenta</button>
            <button class="text-button" type="button" (click)="show('recuperar')">Olvidé mi contraseña</button>
            <button class="text-button" type="button" (click)="show('verificar')">Verificar mi cuenta</button>
          } @else {
            <button class="text-button" type="button" (click)="show('login')">Ya tengo una cuenta</button>
          }
          @if (mode() === 'verificar') {
            <button class="text-button" type="button" (click)="resend(correo)">Reenviar código</button>
          }
        </div>

        @if (session.demoMode) {
          <div class="demo-choices">
            <p>Explora con una cuenta de demostración</p>
            <button class="button secondary" type="button" (click)="demo('student')">Vista estudiante</button>
            <button class="button secondary" type="button" (click)="demo('teacher')">Vista docente</button>
          </div>
        } @else {
          <p class="demo-link">¿Quieres conocer la plataforma? <a href="/?demo=1#/acceso">Explorar demo</a></p>
        }
      </section>
    </div>
  `,
})
export class AuthPage {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly mode = signal<AuthMode>('login');
  readonly data = signal<{ correo?: string; codigo?: string }>({});
  readonly error = signal('');
  readonly busy = signal(false);
  readonly showPassword = signal(false);
  readonly maxDate = maxBirthDate();
  readonly buttons = buttons;
  readonly titles = computed(() => titles[this.mode()]);

  show(next: AuthMode, values: { correo?: string; codigo?: string } = {}): void {
    this.mode.set(next);
    this.data.set(values);
    this.error.set('');
    this.showPassword.set(false);
  }

  async onSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const body = formDataObject(form);
    this.busy.set(true);
    this.error.set('');
    try {
      const mode = this.mode();
      if (mode === 'login') {
        await this.session.signIn({ correo: body['correo'], contrasena: body['contrasena'] });
        return;
      }
      const result = await this.api.request<any>(`/auth/${routes[mode]}`, { method: 'POST', body });
      if (mode === 'registro') this.show('verificar', { correo: body['correo'], codigo: result.codigoVerificacion });
      else if (mode === 'recuperar') { this.toast.show(result.mensaje); this.show('restablecer', { correo: body['correo'], codigo: result.codigo }); }
      else { this.toast.show(result.mensaje); this.show('login', { correo: body['correo'] }); }
    } catch (error) {
      this.error.set((error as Error).message);
    } finally {
      this.busy.set(false);
    }
  }

  async resend(input: HTMLInputElement): Promise<void> {
    if (!input.value || !input.reportValidity()) return;
    const result = await this.api.request<any>('/auth/reenviar-codigo', { method: 'POST', body: { correo: input.value } });
    this.show('verificar', { correo: input.value, codigo: result.codigo });
    this.toast.show(result.mensaje);
  }

  async demo(role: 'student' | 'teacher'): Promise<void> {
    await this.session.signIn({ correo: role === 'teacher' ? 'c12345@utp.edu.pe' : 'u12345678@utp.edu.pe', contrasena: 'Demostracion123' });
  }
}

@Component({
  selector: 'app-complete-profile',
  imports: [IconDirective],
  template: `
    @if (!session.session()) {
      <div class="auth-prompt"><svg appIcon="shield"></svg><div><h2>Completa tu perfil para continuar.</h2><p>Inicia sesión para registrar tu género y fecha de nacimiento.</p></div></div>
    } @else {
      <div class="page-heading">
        <div><div class="eyebrow">UN ÚLTIMO PASO</div><h1>Completa tu perfil.</h1><p>Solo nos faltan dos datos para continuar: tu género y tu fecha de nacimiento.</p></div>
      </div>
      <section class="panel">
        <form id="complete-profile" class="stack-form" (submit)="onSubmit($event)">
          <div class="form-grid">
            <label>Género<select name="genero" required>
              <option value="" disabled selected>Selecciona</option>
              <option value="MASCULINO">Masculino</option>
              <option value="FEMENINO">Femenino</option>
            </select></label>
            <label>Fecha de nacimiento<input type="date" name="fechaNacimiento" [attr.max]="maxDate" required></label>
          </div>
          <div class="form-error" role="alert">{{ error() }}</div>
          <button type="submit" class="button dark" [disabled]="busy()">Guardar y continuar <svg appIcon="arrow"></svg></button>
        </form>
      </section>
    }
  `,
})
export class CompleteProfilePage {
  readonly session = inject(SessionService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly maxDate = maxBirthDate();
  readonly error = signal('');
  readonly busy = signal(false);

  constructor() {
    // Si el perfil ya está completo, esta vista no tiene nada que hacer.
    if (this.session.session() && !needsProfile(this.session.session())) this.router.navigateByUrl('/');
  }

  async onSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const body = formDataObject(event.target as HTMLFormElement);
    this.busy.set(true);
    this.error.set('');
    try {
      const result = await this.api.request<any>(this.session.userPath('perfil'), { method: 'PUT', body });
      this.session.saveProfile(result.genero, result.fechaNacimiento);
      this.toast.show('Perfil completado. ¡Que disfrutes!');
      this.router.navigateByUrl('/');
    } catch (error) {
      this.error.set((error as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
