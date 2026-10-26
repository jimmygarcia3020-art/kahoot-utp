import { Component, inject, signal } from '@angular/core';
import { IconDirective } from '../core/icon.directive';
import { ApiService } from '../core/api.service';
import { PreferencesService } from '../core/preferences.service';
import { SessionService } from '../core/session.service';
import { ToastService } from '../core/ui.service';
import { Preferences, formDataObject } from '../core/util';

@Component({
  selector: 'app-settings',
  imports: [IconDirective],
  template: `
    <div class="page-heading">
      <div><div class="eyebrow">UN ESPACIO QUE SE ADAPTA A TI</div><h1>Aprende a tu manera.</h1><p>Configura la experiencia para sentirte más cómodo.</p></div>
    </div>
    <section class="panel settings-panel">
      <form id="preferences" class="stack-form" (submit)="onSubmit($event)">
        <div class="form-grid">
          <label>Tema visual<select name="tema">
            <option value="NORMAL" [selected]="p().tema === 'NORMAL'">Normal</option>
            <option value="ALTO_CONTRASTE" [selected]="p().tema === 'ALTO_CONTRASTE'">Alto contraste</option>
          </select></label>
          <label>Tamaño del texto<select name="tamanoTexto">
            <option value="NORMAL" [selected]="p().tamanoTexto === 'NORMAL'">Normal</option>
            <option value="GRANDE" [selected]="p().tamanoTexto === 'GRANDE'">Grande</option>
            <option value="MUY_GRANDE" [selected]="p().tamanoTexto === 'MUY_GRANDE'">Muy grande</option>
          </select></label>
        </div>
        @for (s of switches; track s.key) {
          <label class="switch-row">
            <span><strong>{{ s.title }}</strong><small>{{ s.desc }}</small></span>
            <input type="checkbox" [name]="s.key" [checked]="!!p()[s.key]" role="switch">
          </label>
        }
        <p class="muted">{{ session.session() ? 'Las preferencias se guardarán en tu cuenta y en este navegador.' : 'Las preferencias se guardarán en este navegador. Puedes configurarlas antes de iniciar sesión.' }}</p>
        <div class="form-error" role="alert">{{ error() }}</div>
        <button type="submit" class="button dark" [disabled]="busy()">Guardar preferencias <svg appIcon="check"></svg></button>
      </form>
    </section>
  `,
})
export class SettingsPage {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService);
  readonly preferences = inject(PreferencesService);
  private readonly toast = inject(ToastService);
  readonly p = this.preferences.prefs;
  readonly error = signal('');
  readonly busy = signal(false);
  readonly switches: { key: keyof Preferences & string; title: string; desc: string }[] = [
    { key: 'reduccionMovimiento', title: 'Reducir movimiento', desc: 'Evita animaciones y transiciones.' },
    { key: 'sonidoActivado', title: 'Permitir sonido', desc: 'Activa la señal breve al completar un reto.' },
    { key: 'modoDaltonismo', title: 'Diferenciación de colores', desc: 'Usa una paleta azul y naranja, con etiquetas e iconos.' },
  ];

  async onSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const f = new FormData(event.target as HTMLFormElement);
    const next: Preferences = {
      tema: String(f.get('tema')), tamanoTexto: String(f.get('tamanoTexto')),
      reduccionMovimiento: f.has('reduccionMovimiento'), sonidoActivado: f.has('sonidoActivado'), modoDaltonismo: f.has('modoDaltonismo'),
    };
    this.busy.set(true);
    this.error.set('');
    try {
      if (this.session.session()) await this.api.request(this.session.userPath('preferencias'), { method: 'PUT', body: next });
      this.preferences.set(next);
      this.toast.show('Tu espacio ya está configurado.');
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}

