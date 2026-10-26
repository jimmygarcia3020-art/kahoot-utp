import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { ModalService, ToastService } from '../core/ui.service';
import { ConfirmModal, QuizFormModal } from '../modals/modals';
import { EmptyComponent } from '../shared/empty.component';
import { labels } from '../core/util';

@Component({
  selector: 'app-teacher-quizzes',
  imports: [RouterLink, EmptyComponent],
  template: `
    @if (!session.isTeacher()) {
      <app-empty title="Este espacio es para docentes UTP" description="Puedes continuar aprendiendo en el catálogo de micro-retos." />
    } @else {
      <div class="page-heading">
        <div><div class="eyebrow">ESPACIO DOCENTE</div><h1>Prepara tu próxima clase.</h1><p>Reúne tus preguntas y dales un orden para aprender.</p></div>
        <button class="button dark" type="button" (click)="openNew()">+ Crear quiz</button>
      </div>
      <div class="challenge-grid">
        @for (q of quizzes(); track q.id) {
          <article class="challenge-card">
            <span class="pill {{ q.estado === 'LISTO' ? 'completed' : '' }}">{{ label(q.estado) }}</span>
            <h3>{{ q.titulo }}</h3>
            <p>{{ q.items.length }} {{ q.items.length === 1 ? 'pregunta' : 'preguntas' }}</p>
            <ol class="quiz-preview">
              @for (i of q.items.slice(0, 3); track i.id) { <li>{{ i.enunciado }}</li> }
            </ol>
            <div class="card-actions">
              <button class="button secondary" type="button" (click)="openEdit(q)">{{ q.estado === 'LISTO' ? 'Ver quiz' : 'Editar' }}</button>
              @if (q.estado === 'BORRADOR') {
                <button class="button dark" type="button" (click)="markReady(q)">Marcar listo</button>
                <button class="text-button danger" type="button" (click)="confirmDelete(q)">Eliminar</button>
              } @else {
                <a class="text-link" routerLink="/docente/salas">Crear una sala →</a>
              }
            </div>
          </article>
        } @empty {
          <app-empty title="Una nueva clase empieza con una idea" description="Crea un quiz con tus preguntas para preparar una sala." />
        }
      </div>
    }
  `,
})
export class TeacherQuizzesPage {
  readonly session = inject(SessionService);
  private readonly api = inject(ApiService);
  private readonly modal = inject(ModalService);
  private readonly toast = inject(ToastService);

  readonly quizzes = signal<any[]>([]);
  readonly questions = signal<any[]>([]);

  constructor() { this.reload(); }

  label(v: string): string { return labels[v] ?? v; }

  async reload(): Promise<void> {
    if (!this.session.isTeacher()) return;
    const [quizzes, questions] = await Promise.all([
      this.api.request<any[]>(this.session.teacherPath('quizzes')),
      this.api.request<any[]>(this.session.teacherPath('preguntas')),
    ]);
    this.quizzes.set(quizzes);
    this.questions.set(questions);
  }

  openNew(): void { this.modal.open('Un quiz para tu clase', QuizFormModal, { quiz: null, questions: this.questions(), onDone: () => this.reload() }); }

  openEdit(quiz: any): void {
    this.modal.open(quiz.estado === 'LISTO' ? 'Tu quiz está listo' : 'Editar quiz', QuizFormModal, { quiz, questions: this.questions(), onDone: () => this.reload() });
  }

  async markReady(quiz: any): Promise<void> {
    await this.api.request(this.session.teacherPath(`quizzes/${quiz.id}/listo`), { method: 'POST' });
    this.toast.show('Quiz listo para usar.');
    await this.reload();
  }

  confirmDelete(quiz: any): void {
    this.modal.open('Eliminar borrador', ConfirmModal, {
      description: 'Este borrador se eliminará. Las preguntas de tu banco se conservan.',
      run: async () => { await this.api.request(this.session.teacherPath(`quizzes/${quiz.id}`), { method: 'DELETE' }); await this.reload(); },
    });
  }
}
