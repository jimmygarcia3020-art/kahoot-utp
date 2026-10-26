import { Component, computed, inject, input, signal, effect, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { ApiService } from '../core/api.service';
import { CatalogService } from '../core/catalog.service';
import { SessionService } from '../core/session.service';
import { ModalService, ToastService } from '../core/ui.service';
import { ConfirmModal, CloneModal, OptionFormModal, FeedbackFormModal, QuestionFormModal } from '../modals/modals';
import { EmptyComponent } from '../shared/empty.component';
import { labels } from '../core/util';
import { Router } from '@angular/router';

@Component({
  selector: 'app-teacher-questions',
  imports: [RouterLink, IconDirective, EmptyComponent],
  template: `
    @if (!session.isTeacher()) {
      <app-empty title="Este espacio es para docentes UTP" description="Puedes continuar aprendiendo en el catálogo de micro-retos." />
    } @else {
      <div class="page-heading">
        <div><div class="eyebrow">ESPACIO DOCENTE</div><h1>Ideas que se convierten en retos.</h1><p>Crea preguntas y acompaña cada error con una explicación.</p></div>
        <div>
          <button class="button secondary" type="button" (click)="openClone()">Clonar del catálogo</button>
          <button class="button dark" type="button" (click)="openNew()">+ Crear pregunta</button>
        </div>
      </div>
      <div class="challenge-grid">
        @for (q of questions(); track q.id) {
          <article class="challenge-card">
            <div class="row"><span class="pill">{{ label(q.origen) }}</span><span class="pill">{{ label(q.nivel) }}</span></div>
            <span class="overline">{{ label(q.tipo) }}</span>
            <h3>{{ q.enunciado }}</h3>
            <p>{{ catalog.temaOf(q.temaId)?.nombre || 'Tema' }}</p>
            <a class="course-link" [routerLink]="['/docente/preguntas', q.id]">Configurar pregunta <svg appIcon="arrow"></svg></a>
          </article>
        } @empty {
          <app-empty title="Tu banco de ideas está listo" description="Crea tu primera pregunta o clona una del catálogo institucional." />
        }
      </div>
    }
  `,
})
export class TeacherQuestionsPage {
  readonly session = inject(SessionService);
  readonly catalog = inject(CatalogService);
  private readonly api = inject(ApiService);
  private readonly modal = inject(ModalService);

  readonly questions = signal<any[]>([]);

  constructor() {
    this.reload();
  }

  label(v: string): string { return labels[v] ?? v; }

  async reload(): Promise<void> {
    if (!this.session.isTeacher()) return;
    this.questions.set(await this.api.request<any[]>(this.session.teacherPath('preguntas')));
  }

  openNew(): void {
    this.modal.open('Una nueva idea', QuestionFormModal, { question: null, temas: this.catalog.temas(), courses: this.catalog.courses() });
  }

  openClone(): void {
    this.modal.open('Clonar un micro-reto', CloneModal, { retos: this.catalog.retos() });
  }
}

@Component({
  selector: 'app-question-detail',
  imports: [RouterLink, EmptyComponent],
  template: `
    @if (!session.isTeacher()) {
      <app-empty title="Este espacio es para docentes UTP" description="Puedes continuar aprendiendo en el catálogo de micro-retos." />
    } @else if (question(); as q) {
      <a routerLink="/docente/preguntas" class="back-link">← Volver a mis preguntas</a>
      <div class="page-heading">
        <div><div class="eyebrow">ESPACIO DOCENTE</div><h1>Dale forma a tu pregunta.</h1><p>Cada alternativa es una oportunidad para explicar y comprender.</p></div>
        @if (locked()) {
          <span class="pill">Solo lectura</span>
        } @else {
          <div>
            <button class="button secondary" type="button" (click)="editQuestion()">Editar enunciado</button>
            <button class="text-button danger" type="button" (click)="deleteQuestion()">Eliminar pregunta</button>
          </div>
        }
      </div>
      <section class="panel">
        <div class="row"><span class="pill">{{ label(q.tipo) }}</span><span class="pill">{{ label(q.nivel) }}</span></div>
        <h2>{{ q.enunciado }}</h2>
        @if (locked()) {
          <p class="notice info">Esta pregunta está protegida porque es institucional o pertenece a un quiz listo.</p>
        }
        <div class="section-heading">
          <h3>Alternativas y retroalimentación</h3>
          @if (!locked()) { <button class="button dark" type="button" (click)="addOption()">+ Añadir alternativa</button> }
        </div>
        @for (a of alternatives(); track a.id) {
          <article class="alternative-editor">
            <div class="row">
              <strong>{{ a.ordenVisual }}. {{ a.correcta ? '✓ Respuesta correcta' : 'Alternativa incorrecta' }}</strong>
              @if (!locked()) {
                <div>
                  <button class="text-button" type="button" (click)="editOption(a)">Editar</button>
                  <button class="text-button danger" type="button" (click)="deleteOption(a)">Eliminar</button>
                </div>
              }
            </div>
            @if (a.codigo) { <pre><code>{{ a.codigo }}</code></pre> } @else { <p>{{ a.texto }}</p> }
            @if (!a.correcta) {
              <div class="feedback-summary">
                <strong>{{ feedbackOf(a.id) ? '✓ Explicación completa' : '⚠ Falta retroalimentación' }}</strong>
                @if (feedbackOf(a.id); as f) {
                  <dl><dt>Causa</dt><dd>{{ f.causa }}</dd><dt>Explicación</dt><dd>{{ f.explicacion }}</dd><dt>Paso a paso</dt><dd>{{ f.secuenciaTextual }}</dd><dt>Sugerencia</dt><dd>{{ f.sugerencia }}</dd></dl>
                } @else {
                  <p>Completa la causa, explicación, secuencia y sugerencia para poder marcar el quiz listo.</p>
                }
                @if (!locked()) {
                  <button class="button secondary" type="button" (click)="feedbackOption(a)">{{ feedbackOf(a.id) ? 'Editar explicación' : 'Añadir explicación' }}</button>
                }
              </div>
            }
          </article>
        } @empty {
          <app-empty title="Añade tus alternativas" description="Incluye una correcta y explica cada opción incorrecta." />
        }
      </section>
    }
  `,
})
export class QuestionDetailPage {
  readonly id = input.required({ transform: (v: string | number) => Number(v) });
  readonly session = inject(SessionService);
  readonly catalog = inject(CatalogService);
  private readonly api = inject(ApiService);
  private readonly modal = inject(ModalService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly question = signal<any | null>(null);
  readonly alternatives = signal<any[]>([]);
  readonly feedbackList = signal<any[]>([]);
  readonly quizzes = signal<any[]>([]);
  readonly locked = computed(() => {
    const q = this.question();
    return !!q && (q.origen === 'INSTITUCIONAL' || this.quizzes().some(z => z.estado === 'LISTO' && z.items.some((i: any) => i.preguntaId === this.id())));
  });

  private generation = 0;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.reload());
    });
  }

  label(v: string): string { return labels[v] ?? v; }
  feedbackOf(alternativaId: number): any { return this.feedbackList().find(f => f.alternativaId === alternativaId); }
  private base(): string { return this.session.teacherPath(`preguntas/${this.id()}`); }

  async reload(): Promise<void> {
    if (!this.session.isTeacher()) return;
    const current = ++this.generation;
    const base = this.base();
    const [question, alternatives, feedback, quizzes] = await Promise.all([
      this.api.request<any>(base),
      this.api.request<any[]>(`${base}/alternativas`),
      this.api.request<any[]>(`${base}/retroalimentaciones`),
      this.api.request<any[]>(this.session.teacherPath('quizzes')),
    ]);
    if (current !== this.generation) return;
    this.question.set(question);
    this.alternatives.set(alternatives);
    this.feedbackList.set(feedback);
    this.quizzes.set(quizzes);
  }

  editQuestion(): void {
    this.modal.open('Editar pregunta', QuestionFormModal, { question: this.question(), temas: this.catalog.temas(), courses: this.catalog.courses() });
  }

  deleteQuestion(): void {
    this.modal.open('Eliminar pregunta', ConfirmModal, {
      description: 'La pregunta se retirará de tu banco si no está protegida por un quiz listo.',
      run: async () => { await this.api.request(this.base(), { method: 'DELETE' }); this.router.navigateByUrl('/docente/preguntas'); },
    });
  }

  addOption(): void { this.openOption(null); }
  editOption(a: any): void { this.openOption(a); }

  private openOption(option: any | null): void {
    this.modal.open(option ? 'Editar alternativa' : 'Añadir alternativa', OptionFormModal, {
      option, alternatives: this.alternatives(), questionId: this.id(), onDone: () => this.reload(),
    });
  }

  deleteOption(a: any): void {
    this.modal.open('Eliminar alternativa', ConfirmModal, {
      description: 'Esta alternativa se eliminará de la pregunta.',
      run: async () => { await this.api.request(`${this.base()}/alternativas/${a.id}`, { method: 'DELETE' }); await this.reload(); },
    });
  }

  feedbackOption(a: any): void {
    this.modal.open('Convierte el error en aprendizaje', FeedbackFormModal, {
      optionId: a.id, feedback: this.feedbackOf(a.id) ?? null, questionId: this.id(), onDone: () => this.reload(),
    });
  }
}
