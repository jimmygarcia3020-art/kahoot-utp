import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '../core/icon.directive';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { ModalService } from '../core/ui.service';
import { AnnulModal, ConfirmModal, RoomFormModal } from '../modals/modals';
import { EmptyComponent } from '../shared/empty.component';
import { formatTime, labels } from '../core/util';

@Component({
  selector: 'app-teacher-rooms',
  imports: [RouterLink, IconDirective, EmptyComponent],
  template: `
    @if (!session.isTeacher()) {
      <app-empty title="Este espacio es para docentes UTP" description="Puedes continuar aprendiendo en el catálogo de micro-retos." />
    } @else {
      <div class="page-heading">
        <div><div class="eyebrow">ESPACIO DOCENTE</div><h1>El reto se comparte.</h1><p>Dirige tus sesiones y descubre cómo avanza tu clase.</p></div>
        <button class="button dark" type="button" (click)="openNew()">+ Crear sala</button>
      </div>
      <div class="challenge-grid">
        @for (s of rooms(); track s.id) {
          <article class="challenge-card">
            <div class="row"><span class="pill">{{ label(s.estado) }}</span><svg appIcon="users"></svg></div>
            <h3>{{ quizTitle(s) }}</h3>
            <div class="room-pin-label">PIN DE LA SALA<strong>{{ s.pin }}</strong></div>
            <p>{{ s.tiempoPorPregunta }} s por pregunta · {{ ampliacion(s.ampliacion) }}</p>
            <a class="course-link" [routerLink]="['/docente/salas', s.id]">Gestionar sala <svg appIcon="arrow"></svg></a>
          </article>
        } @empty {
          <app-empty title="Tu próxima clase te espera" description="Prepara un quiz listo para usar y crea tu primera sala." />
        }
      </div>
    }
  `,
})
export class TeacherRoomsPage {
  readonly session = inject(SessionService);
  private readonly api = inject(ApiService);
  private readonly modal = inject(ModalService);

  readonly rooms = signal<any[]>([]);
  readonly quizzes = signal<any[]>([]);

  constructor() { this.reload(); }

  label(v: string): string { return labels[v] ?? v; }
  quizTitle(s: any): string { return this.quizzes().find(q => q.id === s.quizId)?.titulo || `Sala ${s.id}`; }
  ampliacion(v: string): string { return v === 'ESTANDAR' ? 'Tiempo estándar' : v === 'MAS_50' ? '+50 % de tiempo' : '+100 % de tiempo'; }

  async reload(): Promise<void> {
    if (!this.session.isTeacher()) return;
    const [rooms, quizzes] = await Promise.all([
      this.api.request<any[]>(this.session.teacherPath('salas')),
      this.api.request<any[]>(this.session.teacherPath('quizzes')),
    ]);
    this.rooms.set(rooms);
    this.quizzes.set(quizzes);
  }

  openNew(): void { this.modal.open('Prepara una sala', RoomFormModal, { quizzes: this.quizzes() }); }
}

@Component({
  selector: 'app-room-detail',
  imports: [RouterLink, IconDirective, EmptyComponent],
  template: `
    @if (!session.isTeacher()) {
      <app-empty title="Este espacio es para docentes UTP" description="Puedes continuar aprendiendo en el catálogo de micro-retos." />
    } @else if (sala(); as s) {
      <a routerLink="/docente/salas" class="back-link">← Volver a mis salas</a>
      <div class="page-heading">
        <div><div class="eyebrow">ESPACIO DOCENTE</div><h1>{{ quiz()?.titulo }}</h1><p>Comparte el PIN y acompaña a tu clase.</p></div>
        <button class="button secondary" type="button" (click)="reload()"><svg appIcon="refresh"></svg></button>
      </div>

      <section class="panel room-control">
        <div><span class="eyebrow">PIN DE LA SALA</span><strong class="large-pin">{{ s.pin }}</strong><span class="pill">{{ label(s.estado) }}</span></div>
        <div class="card-actions">
          @for (t of transitions()[s.estado] || []; track t[0]) {
            <button class="button dark" type="button" (click)="transition(t[0])">{{ t[1] }}</button>
          }
        </div>
        <p>{{ s.tiempoPorPregunta }} segundos por pregunta · {{ activeCount() }} participantes activos</p>
      </section>

      <div class="teacher-room-grid">
        <section class="panel">
          <h2>Participantes</h2>
          @for (p of participants(); track p.id) {
            <div class="participant-row">
              <span class="avatar">{{ p.estudianteId }}</span>
              <div>
                <strong>{{ ranking().find(r => r.participacionId === p.id)?.nombre || 'Estudiante ' + p.estudianteId }}</strong>
                <small>{{ p.expulsado ? 'Participación suspendida' : 'En la sala' }}</small>
              </div>
              @if (s.estado === 'BORRADOR' || s.estado === 'SALA_ESPERA') {
                <button class="text-button" type="button" (click)="moderate(p)">{{ p.expulsado ? 'Readmitir' : 'Expulsar' }}</button>
              }
            </div>
          } @empty {
            <app-empty title="Aquí se reúne tu clase" description="Abre la espera y comparte el PIN." />
          }
        </section>
        <section class="panel">
          <h2>Clasificación</h2>
          @if (ranking().length) {
            <div class="table-scroll"><table>
              <thead><tr><th>#</th><th>Estudiante</th><th>Puntos</th><th>Tiempo</th></tr></thead>
              <tbody>
                @for (r of ranking(); track r.posicion) {
                  <tr><td>{{ r.posicion }}</td><td>{{ r.nombre }} {{ r.apellidoInicial }}</td><td>{{ r.puntos }}</td><td>{{ fmt(r.tiempoMs) }}</td></tr>
                }
              </tbody>
            </table></div>
          } @else {
            <app-empty title="Los resultados llegarán aquí" description="Los aciertos suman y el tiempo solo desempata." />
          }
        </section>
      </div>

      <section class="panel">
        <div class="section-heading"><h2>Preguntas de la sesión</h2><span>{{ quiz()?.items.length }} preguntas</span></div>
        @for (item of quiz()?.items || []; track item.id) {
          <div class="session-question">
            <span class="block-index">{{ item.orden }}</span>
            <div>
              <strong>{{ item.enunciado }}</strong>
              @if (annulmentOf(item.preguntaId); as an) { <p>Anulada: {{ an.motivo }}</p> }
            </div>
            @if (s.estado !== 'FINALIZADA' && !annulmentOf(item.preguntaId)) {
              <button class="text-button danger" type="button" (click)="annul(item.preguntaId)">Anular</button>
            }
          </div>
        }
      </section>
    }
  `,
})
export class RoomDetailPage {
  readonly id = input.required({ transform: (v: string | number) => Number(v) });
  readonly session = inject(SessionService);
  private readonly api = inject(ApiService);
  private readonly modal = inject(ModalService);

  readonly sala = signal<any | null>(null);
  readonly quiz = signal<any | null>(null);
  readonly participants = signal<any[]>([]);
  readonly ranking = signal<any[]>([]);
  readonly anulaciones = signal<any[]>([]);
  readonly activeCount = computed(() => this.participants().filter(p => !p.expulsado).length);
  readonly transitions = signal<Record<string, [string, string][]>>({
    BORRADOR: [['abrir-espera', 'Abrir sala de espera'], ['iniciar', 'Iniciar sala']],
    SALA_ESPERA: [['iniciar', 'Iniciar sala']],
    EN_CURSO: [['pausar', 'Pausar sala'], ['finalizar', 'Finalizar sala']],
    PAUSADA: [['reanudar', 'Reanudar sala'], ['finalizar', 'Finalizar sala']],
    FINALIZADA: [],
  });

  private generation = 0;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.reload());
    });
  }

  label(v: string): string { return labels[v] ?? v; }
  fmt(ms: number): string { return formatTime(ms); }
  annulmentOf(preguntaId: number): any { return this.anulaciones().find(a => a.preguntaId === preguntaId); }
  private base(): string { return this.session.teacherPath(`salas/${this.id()}`); }

  async reload(): Promise<void> {
    if (!this.session.isTeacher()) return;
    const current = ++this.generation;
    const base = this.base();
    const [sala, participants, ranking, anulaciones] = await Promise.all([
      this.api.request<any>(base),
      this.api.request<any[]>(`/salas/${this.id()}/participaciones`),
      this.api.request<any[]>(`/salas/${this.id()}/ranking`),
      this.api.request<any[]>(`${base}/anulaciones`),
    ]);
    const quiz = await this.api.request<any>(this.session.teacherPath(`quizzes/${sala.quizId}`));
    if (current !== this.generation) return;
    this.sala.set(sala);
    this.quiz.set(quiz);
    this.participants.set(participants);
    this.ranking.set(ranking);
    this.anulaciones.set(anulaciones);
  }

  async transition(action: string): Promise<void> {
    const change = async () => { await this.api.request(`${this.base()}/${action}`, { method: 'POST' }); await this.reload(); };
    if (action !== 'finalizar') return change();
    this.modal.open('Finalizar la sala', ConfirmModal, {
      description: 'La sesión pasará a finalizada y no se podrá reanudar.', run: change,
    });
  }

  async moderate(p: any): Promise<void> {
    const mode = p.expulsado ? 'readmitir' : 'expulsar';
    await this.api.request(`/salas/${this.id()}/participaciones/${p.id}/${mode}?docenteId=${this.session.session()!.usuarioId}`, { method: 'POST' });
    await this.reload();
  }

  annul(preguntaId: number): void {
    this.modal.open('Anular una pregunta', AnnulModal, { salaId: this.id(), preguntaId, onDone: () => this.reload() });
  }
}
