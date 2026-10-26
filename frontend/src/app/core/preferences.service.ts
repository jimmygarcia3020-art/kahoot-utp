import { Injectable, signal } from '@angular/core';
import { Preferences, defaultPreferences, readStored } from './util';

// Accesibilidad: se aplica en <html> mediante data-attributes, como en el diseño original.
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  readonly prefs = signal<Preferences>({ ...defaultPreferences, ...readStored<Partial<Preferences>>(localStorage, 'adaptcode.preferences', {}) });

  constructor() { this.apply(); }

  set(next: Partial<Preferences>): void {
    this.prefs.set({ ...defaultPreferences, ...next });
    this.apply();
  }

  apply(): void {
    const p = this.prefs(), root = document.documentElement;
    root.dataset['contrast'] = p.tema === 'ALTO_CONTRASTE' ? 'high' : 'normal';
    root.dataset['text'] = p.tamanoTexto;
    root.dataset['motion'] = p.reduccionMovimiento ? 'reduced' : 'normal';
    root.dataset['color'] = p.modoDaltonismo ? 'accessible' : 'normal';
    localStorage.setItem('adaptcode.preferences', JSON.stringify(p));
  }
}
