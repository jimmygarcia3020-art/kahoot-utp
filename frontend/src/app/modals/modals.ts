import { Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { ModalService } from '../core/ui.service';
import { IconDirective } from '../core/icon.directive';
import { EmptyComponent } from '../shared/empty.component';
import { formatTime, formDataObject, labels } from '../core/util';

// Base común: cada formulario muestra su error dentro de la ventana y se cierra al guardar.
export abstract class FormModal {
  readonly modal = inject(ModalService);
  protected readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);
  protected readonly router = inject(Router);
  readonly error = signal('');
  readonly busy = signal(false);

  protected async send(event: SubmitEvent, action: (form: HTMLFormElement) => Promise<void>): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    this.busy.set(true);
    this.error.set('');
    try {
      await action(form);
      this.modal.close();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }

  protected get teacher(): (resource: string) => string { return resource => this.session.teacherPath(resource); }
}

const levelOptions = ['BASICO', 'INTERMEDIO', 'AVANZADO'];
const typeOptions = ['SELECCION_MULTIPLE', 'ORDENAR_BLOQUES', 'PREDECIR_SALIDA', 'SELECCIONAR_FRAGMENTO'];

@Component({
  selector: 'app-help-modal',
  template: `
    <div class="help-content">
      <h3>¿Por dónde empiezo?</h3><p>Explora un curso, filtra los micro-retos y elige uno. Inicia sesión para enviar respuestas y guardar tu progreso.</p>
      <h3>¿Y si me equivoco?</h3><p>Lee la explicación y vuelve a intentarlo. Los reintentos son ilimitados. Desde el tercer intento, la API permite consultar la solución; usarla completa el reto con ayuda.</p>
      <h3>¿Cómo entro a mi clase?</h3><p>Usa tu cuenta de estudiante UTP e ingresa el PIN durante la sala de espera.</p>
      <h3>Hazlo a tu manera</h3><p>En Accesibilidad puedes ajustar el contraste, texto, movimiento y sonido.</p>
    </div>`,
})
export class HelpModal {}

@Component({
  selector: 'app-profile-modal',
  imports: [RouterLink, IconDirective],
  template: `
    @if (session.session(); as s) {
      <p><strong>{{ s.nombres }} {{ s.apellidos }}</strong><br>{{ s.correo }}</p>
      <p>{{ roleLabel(s.rol) }}</p>
      <a class="button secondary" routerLink="/ajustes" id="profile-settings" (click)="modal.close()">Preferencias de accesibilidad</a>
      <button class="button dark" id="logout" type="button" (click)="logout()"><svg appIcon="logout"></svg> Cerrar sesión</button>
    }`,
})
export class ProfileModal {
  readonly modal = inject(ModalService);
  readonly session = inject(SessionService);
  roleLabel(rol: string): string { return labels[rol] ?? ''; }
  logout(): void { this.modal.close(); this.session.signOut(); }
}

@Component({
  selector: 'app-confirm-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <p>{{ description() }}</p>
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" type="submit" [disabled]="busy()">Confirmar</button>
    </form>`,
})
export class ConfirmModal extends FormModal {
  readonly description = input('');
  readonly run = input.required<() => Promise<void>>();
  submit(event: SubmitEvent): Promise<void> { return super.send(event, () => this.run()()); }
}

@Component({
  selector: 'app-clone-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <p>Se creará una copia privada. Configura sus alternativas y retroalimentación antes de usarla en un quiz.</p>
      <label>Micro-reto institucional<select name="id" required><option value="">Selecciona un reto</option>
        @for (r of retos(); track r.id) { <option [value]="r.id">{{ r.enunciado }}</option> }
      </select></label>
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" [disabled]="busy()">Crear copia privada</button>
    </form>`,
})
export class CloneModal extends FormModal {
  readonly retos = input.required<any[]>();
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = new FormData(form);
      const q = await this.api.request(this.teacher(`preguntas/${Number(f.get('id'))}/clonar`), { method: 'POST' });
      this.router.navigateByUrl(`/docente/preguntas/${q.id}`);
    });
  }
}

@Component({
  selector: 'app-room-form-modal',
  imports: [],
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <label>Quiz listo para usar<select name="quizId" required><option value="">Selecciona un quiz</option>
        @for (q of readyQuizzes(); track q.id) { <option [value]="q.id">{{ q.titulo }}</option> }
      </select></label>
      <div class="form-grid">
        <label>Tiempo por pregunta<select name="tiempoPorPregunta">
          @for (t of times; track t) { <option [value]="t" [selected]="t === 60">{{ t }} segundos</option> }
        </select></label>
        <label>Ampliación para toda la sala<select name="ampliacion">
          <option value="ESTANDAR">Estándar</option><option value="MAS_50">+50 %</option><option value="MAS_100">+100 %</option>
        </select></label>
      </div>
      <p class="field-hint">Abre la sala de espera para que tus estudiantes puedan ingresar.</p>
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" [disabled]="busy()">Crear sala y generar PIN</button>
    </form>`,
})
export class RoomFormModal extends FormModal {
  readonly quizzes = input.required<any[]>();
  readonly times = [20, 30, 60, 90, 120];
  readonly readyQuizzes = computed(() => this.quizzes().filter(q => q.estado === 'LISTO'));
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = new FormData(form);
      const s = await this.api.request(this.teacher('salas'), { method: 'POST', body: { quizId: Number(f.get('quizId')), tiempoPorPregunta: Number(f.get('tiempoPorPregunta')), ampliacion: f.get('ampliacion') } });
      this.router.navigateByUrl(`/docente/salas/${s.id}`);
    });
  }
}

@Component({
  selector: 'app-question-form-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <label>Tema<select name="temaId" required><option value="">Selecciona un tema</option>
        @for (t of temas(); track t.id) { <option [value]="t.id" [selected]="t.id === question()?.temaId">{{ courseName(t.cursoId) }} · {{ t.nombre }}</option> }
      </select></label>
      <div class="form-grid">
        <label>Tipo<select name="tipo">
          @for (v of types; track v) { <option [value]="v" [selected]="v === question()?.tipo">{{ label(v) }}</option> }
        </select></label>
        <label>Nivel<select name="nivel">
          @for (v of levels; track v) { <option [value]="v" [selected]="v === question()?.nivel">{{ label(v) }}</option> }
        </select></label>
      </div>
      <label>Enunciado<textarea name="enunciado" maxlength="1000" rows="4" required>{{ question()?.enunciado }}</textarea></label>
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" [disabled]="busy()">Guardar pregunta <svg appIcon="check"></svg></button>
    </form>`,
  imports: [IconDirective],
})
export class QuestionFormModal extends FormModal {
  readonly question = input<any | null>(null);
  readonly temas = input.required<any[]>();
  readonly courses = input.required<any[]>();
  readonly types = typeOptions;
  readonly levels = levelOptions;
  label(v: string): string { return labels[v] ?? v; }
  courseName(cursoId: number): string { return this.courses().find(c => c.id === cursoId)?.nombre ?? ''; }
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = new FormData(form), question = this.question();
      const q = await this.api.request(this.teacher(`preguntas${question ? `/${question.id}` : ''}`), {
        method: question ? 'PUT' : 'POST',
        body: { temaId: Number(f.get('temaId')), tipo: f.get('tipo'), nivel: f.get('nivel'), enunciado: f.get('enunciado') },
      });
      this.router.navigateByUrl(`/docente/preguntas/${q.id}`);
    });
  }
}

@Component({
  selector: 'app-option-form-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <label>Texto de la respuesta<textarea name="texto" maxlength="1000" rows="2">{{ option()?.texto }}</textarea></label>
      <label>Código (opcional)<textarea class="code-input" name="codigo" maxlength="4000" rows="3">{{ option()?.codigo }}</textarea></label>
      <div class="form-grid">
        <label>Lenguaje<input name="lenguaje" maxlength="32" [value]="option()?.lenguaje ?? ''" placeholder="python, sql…"></label>
        <label>Orden visual<input type="number" min="1" name="ordenVisual" [value]="option()?.ordenVisual || defaultOrder()" required></label>
      </div>
      <label class="checkbox-label"><input type="checkbox" name="correcta" [checked]="!!option()?.correcta"> Esta es la respuesta correcta</label>
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" [disabled]="busy()">Guardar alternativa</button>
    </form>`,
})
export class OptionFormModal extends FormModal {
  readonly option = input<any | null>(null);
  readonly alternatives = input.required<any[]>();
  readonly questionId = input.required<number>();
  readonly onDone = input.required<() => void>();
  defaultOrder(): number { return Math.max(0, ...this.alternatives().map(a => a.ordenVisual)) + 1; }
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = new FormData(form), option = this.option();
      if (!String(f.get('texto')).trim() && !String(f.get('codigo')).trim()) throw new Error('Escribe un texto o fragmento de código.');
      await this.api.request(this.teacher(`preguntas/${this.questionId()}/alternativas${option ? `/${option.id}` : ''}`), {
        method: option ? 'PUT' : 'POST',
        body: { texto: f.get('texto') || null, codigo: f.get('codigo') || null, lenguaje: f.get('lenguaje') || null, ordenVisual: Number(f.get('ordenVisual')), correcta: f.has('correcta') },
      });
      this.onDone()();
    });
  }
}

@Component({
  selector: 'app-feedback-form-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      @for (field of fields; track field[0]) {
        <label>{{ field[1] }}<textarea [name]="field[0]" [attr.maxlength]="field[2]" rows="3" required>{{ feedback()?.[field[0]] }}</textarea></label>
      }
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" [disabled]="busy()">Guardar explicación</button>
    </form>`,
})
export class FeedbackFormModal extends FormModal {
  readonly optionId = input.required<number>();
  readonly feedback = input<any | null>(null);
  readonly questionId = input.required<number>();
  readonly onDone = input.required<() => void>();
  readonly fields: [string, string, number][] = [
    ['causa', '¿Qué causó el error?', 500],
    ['explicacion', 'Explicación conceptual', 1500],
    ['secuenciaTextual', 'Secuencia paso a paso', 1500],
    ['sugerencia', 'Sugerencia para reintentar', 500],
  ];
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = this.feedback();
      await this.api.request(this.teacher(`preguntas/${this.questionId()}/retroalimentaciones${f ? `/${f.id}` : ''}`), {
        method: f ? 'PUT' : 'POST',
        body: { alternativaId: this.optionId(), ...formDataObject(form) },
      });
      this.onDone()();
    });
  }
}

@Component({
  selector: 'app-quiz-form-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <label>Título<input name="titulo" minlength="3" maxlength="120" [value]="quiz()?.titulo ?? ''" required [disabled]="locked()"></label>
      <p>Selecciona tus preguntas y define un orden único para cada una.</p>
      <fieldset [disabled]="locked()">
        <legend>Preguntas del quiz</legend>
        <div class="quiz-question-list">
          @for (q of sorted(); track q.id; let index = $index) {
            <div class="quiz-question">
              <label><input type="checkbox" name="pregunta" value="{{ q.id }}" [checked]="!!itemOf(q.id)"><span>{{ q.enunciado }}</span></label>
              <label class="quiz-order">Orden<input type="number" name="order-{{ q.id }}" min="1" [value]="itemOf(q.id)?.orden || index + 1" [attr.aria-label]="'Orden de pregunta ' + q.id"></label>
            </div>
          } @empty {
            <p>Aún no tienes preguntas. Crea una en tu banco de preguntas.</p>
          }
        </div>
      </fieldset>
      @if (locked()) {
        <p class="notice info">Los quizzes listos están protegidos contra cambios.</p>
      } @else {
        <div class="form-error" role="alert">{{ error() }}</div>
        <button class="button dark" [disabled]="busy()">Guardar borrador</button>
      }
    </form>`,
})
export class QuizFormModal extends FormModal {
  readonly quiz = input<any | null>(null);
  readonly questions = input.required<any[]>();
  readonly onDone = input.required<() => void>();
  readonly locked = computed(() => this.quiz()?.estado === 'LISTO');
  itemOf(preguntaId: number): any { return this.quiz()?.items.find((i: any) => i.preguntaId === preguntaId); }
  readonly sorted = computed(() => [...this.questions()].sort((a, b) => (this.itemOf(a.id)?.orden || 9999) - (this.itemOf(b.id)?.orden || 9999)));
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = new FormData(form), quiz = this.quiz();
      const items = f.getAll('pregunta').map(id => ({ preguntaId: Number(id), orden: Number(f.get(`order-${id}`)) })).sort((a, b) => a.orden - b.orden);
      if (items.some(i => !Number.isInteger(i.orden) || i.orden < 1)) throw new Error('Cada pregunta seleccionada necesita un orden positivo.');
      if (new Set(items.map(i => i.orden)).size !== items.length) throw new Error('El orden de las preguntas no puede repetirse.');
      await this.api.request(this.teacher(`quizzes${quiz ? `/${quiz.id}` : ''}`), { method: quiz ? 'PUT' : 'POST', body: { titulo: f.get('titulo'), items } });
      this.onDone()();
    });
  }
}

@Component({
  selector: 'app-annul-modal',
  template: `
    <form class="stack-form" (submit)="submit($event)">
      <p>La pregunta se excluirá de los puntos y de la clasificación de esta sala.</p>
      <label>Motivo<textarea name="motivo" maxlength="500" required rows="3"></textarea></label>
      <div class="form-error" role="alert">{{ error() }}</div>
      <button class="button dark" [disabled]="busy()">Anular pregunta</button>
    </form>`,
})
export class AnnulModal extends FormModal {
  readonly salaId = input.required<number>();
  readonly preguntaId = input.required<number>();
  readonly onDone = input.required<() => void>();
  submit(event: SubmitEvent): Promise<void> {
    return super.send(event, async form => {
      const f = new FormData(form);
      await this.api.request(this.teacher(`salas/${this.salaId()}/anulaciones`), { method: 'POST', body: { preguntaId: this.preguntaId(), motivo: f.get('motivo') } });
      this.onDone()();
    });
  }
}

@Component({
  selector: 'app-history-modal',
  imports: [EmptyComponent],
  template: `
    @if (history().length) {
      <div class="table-scroll"><table>
        <thead><tr><th>Intento</th><th>Resultado</th><th>Ayuda</th><th>Tiempo</th></tr></thead>
        <tbody>
          @for (h of history(); track h.id) {
            <tr><td>{{ h.nroIntento }}</td><td>{{ h.acierto ? '✓ Correcto' : 'Por revisar' }}</td><td>{{ h.usoAyuda ? 'Con solución' : 'Sin solución' }}</td><td>{{ fmt(h.tiempoMs) }}</td></tr>
          }
        </tbody>
      </table></div>
    } @else {
      <app-empty title="Aquí empieza tu recorrido" description="Todavía no has enviado respuestas a este reto." />
    }`,
})
export class HistoryModal {
  readonly history = input.required<any[]>();
  fmt(ms: number): string { return formatTime(ms); }
}
