import { Component, OnDestroy, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { ApiService } from '../core/api.service';
import { CatalogService } from '../core/catalog.service';
import { SessionService } from '../core/session.service';
import { PreferencesService } from '../core/preferences.service';
import { ModalService } from '../core/ui.service';
import { HistoryModal } from '../modals/modals';
import { evaluationPayload, labels } from '../core/util';

// Práctica de un micro-reto: la evaluación ocurre en el servidor, nunca en el navegador.
@Component({
  selector: 'app-practice',
  imports: [RouterLink, IconDirective],
  template: `
    @if (reto(); as r) {
      <a class="back-link" routerLink="/retos">← Volver a los micro-retos</a>
      <div class="page-heading">
        <div>
          <div class="eyebrow">{{ courseName() }} · {{ temaName() }}</div>
          <h1>Un intento, una nueva idea.</h1>
          <p>Lee con calma. Aquí tienes espacio para comprender.</p>
        </div>
        <span class="pill">{{ label(r.nivel) }}</span>
      </div>

      <div class="practice-layout">
        <section class="panel practice-panel">
          <div class="row">
            <span class="tag"><svg appIcon="code"></svg> {{ label(r.tipo) }}</span>
            <span class="pill">{{ attemptLabel() }}</span>
          </div>
          <h2>{{ r.enunciado }}</h2>

          @if (r.contenido.codigo; as codigo) {
            <div class="code-block"><div>{{ codigo.lenguaje || 'Código' }}</div><pre><code>{{ codigo.codigo }}</code></pre></div>
          }

          <form id="answer-form" (submit)="onSubmit($event)">
            <fieldset>
              <legend>{{ r.tipo === 'ORDENAR_BLOQUES' ? 'Ordena los bloques con los botones de subir y bajar.' : 'Selecciona una respuesta.' }}</legend>
              @if (r.tipo === 'ORDENAR_BLOQUES') {
                <div id="answer-options">
                  @for (block of blocks(); track $index; let i = $index) {
                    <div class="block-option">
                      <span class="block-index">{{ i + 1 }}</span><code>{{ block.texto }}</code>
                      <div>
                        <button type="button" class="icon-button" [attr.data-move]="i" aria-label="Subir bloque {{ i + 1 }}" [disabled]="i === 0" (click)="move(i, -1)">↑</button>
                        <button type="button" class="icon-button" [attr.data-move]="i" aria-label="Bajar bloque {{ i + 1 }}" [disabled]="i === blocks().length - 1" (click)="move(i, 1)">↓</button>
                      </div>
                    </div>
                  }
                </div>
              } @else {
                <div id="answer-options">
                  @for (op of r.contenido.opciones || []; track op.id; let i = $index) {
                    <label class="answer-option">
                      <input type="radio" name="answer" [value]="op.id" required>
                      <span class="option-letter">{{ letter(i) }}</span>
                      @if (op.codigo) { <pre><code>{{ op.codigo }}</code></pre> } @else { <span>{{ op.texto }}</span> }
                    </label>
                  }
                </div>
              }
            </fieldset>

            <div id="feedback" aria-live="polite">
              @if (solutionShown(); as solution) {
                <div class="feedback"><h3><svg appIcon="bulb"></svg> Solución explicada</h3><pre><code>{{ solution.respuestaCorrecta }}</code></pre><p>{{ solution.detalle }}</p><p><strong>Reto completado con ayuda.</strong> Este avance cuenta para tu progreso.</p></div>
              } @else if (result(); as res) {
                <div class="feedback {{ res.acierto ? 'correct' : 'retry-feedback' }}">
                  <h3><svg [appIcon]="res.acierto ? 'check' : 'bulb'"></svg> {{ res.acierto ? '¡Lo comprendiste! Buen trabajo.' : 'Vamos a mirarlo de otra manera.' }}</h3>
                  @if (res.acierto) {
                    <p>Tu respuesta es correcta. Puedes volver a practicar o explorar otro reto.</p>
                  } @else if (res.retroalimentacion) {
                    <dl>
                      <dt>¿Qué pasó?</dt><dd>{{ res.retroalimentacion.causa }}</dd>
                      <dt>La idea clave</dt><dd>{{ res.retroalimentacion.explicacion }}</dd>
                      <dt>Paso a paso</dt><dd>{{ res.retroalimentacion.secuenciaTextual }}</dd>
                      <dt>Para tu siguiente intento</dt><dd>{{ res.retroalimentacion.sugerencia }}</dd>
                    </dl>
                  } @else {
                    <p>Revisa el orden y la lógica de los pasos. Puedes volver a intentarlo.</p>
                  }
                  @if (!res.acierto && res.verSolucionDisponible && res.solucion) {
                    <button type="button" class="button secondary" (click)="showSolution()">Ver solución y completar con ayuda</button>
                  }
                </div>
              }
            </div>

            <div class="practice-actions">
              <button type="submit" class="button dark" id="evaluate">{{ result()?.acierto ? 'Volver a comprobar' : (result() ? 'Intentar de nuevo' : 'Comprobar respuesta') }} <svg appIcon="arrow"></svg></button>
              <button type="button" class="text-button" (click)="openHistory()"><svg appIcon="clock"></svg> Ver mis intentos</button>
            </div>
          </form>
        </section>

        <aside>
          <section class="panel practice-guide">
            <span class="stat-icon mint"><svg appIcon="bulb"></svg></span>
            <h3>Comprender es la meta.</h3>
            <p>Si te equivocas, revisa la explicación y vuelve a probar. Cada intento te acerca un poco más.</p>
            <ul>
              <li>Los intentos son ilimitados.</li>
              <li>El tiempo no cambia tus puntos.</li>
              <li>La solución asistida no otorga punto de dominio.</li>
            </ul>
            <label>Tu modo de práctica
              <select id="practice-mode" (change)="setMode(+$any($event.target).value)">
                <option value="0">Guiado · Sin tiempo</option>
                @for (t of times; track t) { <option [value]="t">Desafío personal · {{ t }} s</option> }
              </select>
            </label>
            <p id="practice-timer" class="timer" role="status">{{ timerText() }}</p>
          </section>
          @if (!session.session()) {
            <div class="notice info"><div><strong>Guarda cada paso.</strong><p>Inicia sesión para enviar tu respuesta y conservar tus logros.</p><a class="text-link" routerLink="/acceso">Iniciar sesión →</a></div></div>
          }
        </aside>
      </div>
    }
  `,
})
export class PracticePage implements OnDestroy {
  readonly id = input.required({ transform: (v: string | number) => Number(v) });
  private readonly api = inject(ApiService);
  private readonly catalog = inject(CatalogService);
  readonly session = inject(SessionService);
  private readonly preferences = inject(PreferencesService);
  private readonly modal = inject(ModalService);

  readonly times = [20, 30, 60, 90, 120];
  readonly reto = signal<any | null>(null);
  readonly blocks = signal<any[]>([]);
  readonly result = signal<any | null>(null);
  readonly solutionShown = signal<any | null>(null);
  readonly attemptLabel = computed(() => this.result() ? `Intento ${this.result().nroIntento}` : 'Sin límite de intentos');
  readonly timerText = signal('A tu propio ritmo');
  readonly courseName = computed(() => this.catalog.courseOf(this.catalog.retoOf(this.id())?.cursoId)?.nombre || 'PRÁCTICA AUTÓNOMA');
  readonly temaName = computed(() => this.catalog.temaOf(this.reto()?.temaId)?.nombre ?? '');

  private generation = 0;
  private started = Date.now();
  private usedHelp = false;
  private timer: ReturnType<typeof setInterval> | undefined;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
  }

  ngOnDestroy(): void { clearInterval(this.timer); }

  label(v: string): string { return labels[v] ?? v; }
  letter(i: number): string { return String.fromCharCode(65 + i); }

  private async load(id: number): Promise<void> {
    const current = ++this.generation;
    this.result.set(null); this.solutionShown.set(null); this.usedHelp = false; this.started = Date.now();
    const reto = await this.api.request<any>(`/micro-retos/${id}`);
    if (current !== this.generation) return;
    this.reto.set(reto);
    this.blocks.set([...(reto.contenido.bloques || [])]);
  }

  move(index: number, direction: number): void {
    const next = index + direction, list = [...this.blocks()];
    [list[index], list[next]] = [list[next], list[index]];
    this.blocks.set(list);
    setTimeout(() => document.querySelector<HTMLElement>(`[data-move="${next}"]:not(:disabled)`)?.focus());
  }

  setMode(seconds: number): void {
    clearInterval(this.timer);
    if (!seconds) { this.timerText.set('A tu propio ritmo'); return; }
    const until = Date.now() + seconds * 1000;
    const tick = () => {
      const remaining = Math.max(0, Math.ceil((until - Date.now()) / 1000));
      this.timerText.set(remaining ? `${remaining} s para tu desafío personal` : 'Tiempo cumplido. Puedes seguir a tu ritmo.');
      if (!remaining) clearInterval(this.timer);
    };
    tick();
    this.timer = setInterval(tick, 1000);
  }

  async onSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    if (!this.session.requireSession()) return;
    const reto = this.reto();
    const answer = reto.tipo === 'ORDENAR_BLOQUES' ? this.blocks().map(b => b.id) : new FormData(event.target as HTMLFormElement).get('answer');
    // El servidor califica; el catálogo público no incluye alternativas correctas.
    const result = await this.api.request<any>(this.session.userPath('intentos/evaluar'), {
      method: 'POST', body: evaluationPayload(reto, answer, this.started, this.usedHelp),
    });
    this.result.set(result);
    if (result.acierto && this.preferences.prefs().sonidoActivado) playSuccess();
    this.started = Date.now();
    await this.session.loadPersonal(true);
  }

  async showSolution(): Promise<void> {
    const result = this.result();
    if (!result?.solucion || !this.session.requireSession()) return;
    await this.api.request(this.session.userPath('intentos'), {
      method: 'POST', body: { microRetoId: this.id(), tiempoMs: Math.max(0, Date.now() - this.started) },
    });
    this.usedHelp = true;
    this.solutionShown.set(result.solucion);
    await this.session.loadPersonal(true);
  }

  async openHistory(): Promise<void> {
    if (!this.session.requireSession()) return;
    const history = await this.api.request<any[]>(this.session.userPath(`intentos?microRetoId=${this.id()}`));
    this.modal.open('Cada intento es parte del camino', HistoryModal, { history });
  }
}

function playSuccess(): void {
  try {
    const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioCtx) return;
    const audio = new AudioCtx(), oscillator = audio.createOscillator(), gain = audio.createGain();
    oscillator.connect(gain); gain.connect(audio.destination); gain.gain.value = 0.035;
    oscillator.frequency.setValueAtTime(523.25, audio.currentTime); oscillator.frequency.setValueAtTime(659.25, audio.currentTime + 0.12);
    gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.3);
    oscillator.start(); oscillator.stop(audio.currentTime + 0.3);
    oscillator.onended = () => audio.close();
  } catch { /* El audio es opcional; la señal visual siempre permanece. */ }
}
