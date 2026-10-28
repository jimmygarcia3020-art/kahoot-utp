import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { IconDirective } from '../core/icon.directive';
import { AuthPromptComponent } from '../shared/notices.component';
import { EmptyComponent } from '../shared/empty.component';
import { labels } from '../core/util';

@Component({
  selector: 'app-student-rooms',
  imports: [RouterLink, IconDirective, AuthPromptComponent, EmptyComponent],
  template: `
    @if (!session.session()) {
      <app-auth-prompt title="El aprendizaje también se comparte." text="Inicia sesión con tu cuenta de estudiante UTP para unirte a una sala." />
    } @else if (session.session()?.rol !== 'ESTUDIANTE_UTP') {
      <app-empty title="Salas para estudiantes UTP" description="Las cuentas externas tienen acceso a toda la práctica autónoma.">
        <a class="button dark" routerLink="/retos">Explorar micro-retos</a>
      </app-empty>
    } @else {
      <div class="page-heading">
        <div><div class="eyebrow">COMPARTIR EL RETO, MULTIPLICAR LAS IDEAS</div><h1>Salas en vivo</h1><p>Tu clase, un PIN y muchas ganas de aprender.</p></div>
      </div>
      <section class="room-join panel">
        <span class="stat-icon mint"><svg appIcon="users"></svg></span>
        <h2>Encuentra a tu clase.</h2>
        <p>Ingresa los seis dígitos que te comparte tu docente.</p>
        <form id="room-pin" class="pin-form" (submit)="search($event)">
          <label class="sr-only" for="pin">PIN de sala</label>
          <input id="pin" name="pin" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" [value]="pin()" required placeholder="000000">
          <button class="button dark" type="submit">Buscar sala <svg appIcon="arrow"></svg></button>
        </form>
      </section>

      @if (sala(); as s) {
        <section class="panel room-detail">
          <div class="row">
            <div><span class="eyebrow">SALA ENCONTRADA</span><h2>PIN <span class="pin-code">{{ s.pin }}</span></h2></div>
            <span class="pill">{{ label(s.estado) }}</span>
          </div>
          <p>{{ activeCount() }} participantes · {{ s.tiempoPorPregunta }} segundos por pregunta</p>

          @if (participation()?.expulsado) {
            <div class="notice warning">Tu participación está suspendida. Consulta con tu docente.</div>
          } @else if (participation()) {
            <div class="notice info"><svg appIcon="check"></svg> {{ participationText(s.estado) }}</div>
          } @else if (s.estado === 'SALA_ESPERA') {
            <button class="button dark" type="button" (click)="join(s)">Unirme a esta sala</button>
          } @else {
            <div class="notice warning">El ingreso está disponible únicamente durante la sala de espera.</div>
          }

          <button class="button secondary" type="button" (click)="reload()"><svg appIcon="refresh"></svg> Actualizar sala</button>

          <div class="section-heading"><h3>Clasificación de la sala</h3><span>Los aciertos cuentan. El tiempo desempata.</span></div>
          @if (ranking().length) {
            <div class="table-scroll"><table>
              <thead><tr><th>Posición</th><th>Estudiante</th><th>Puntos</th><th>Tiempo</th></tr></thead>
              <tbody>
                @for (r of ranking(); track r.posicion) {
                  <tr>
                    <td><span class="rank-number">{{ r.posicion }}</span></td>
                    <td>{{ r.nombre }} {{ r.apellidoInicial }}</td>
                    <td>{{ r.puntos }}</td>
                    <td>{{ seconds(r.tiempoMs) }}</td>
                  </tr>
                }
              </tbody>
            </table></div>
          } @else {
            <app-empty title="La clasificación está por empezar" description="Los resultados aparecerán después de las respuestas." />
          }
        </section>
      }
    }
  `,
})
export class StudentRoomsPage {
  readonly pin = input('');
  readonly session = inject(SessionService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly sala = signal<any | null>(null);
  readonly participants = signal<any[]>([]);
  readonly ranking = signal<any[]>([]);
  readonly participation = computed(() => this.participants().find(p => p.estudianteId === this.session.session()?.usuarioId));
  readonly activeCount = computed(() => this.participants().filter(p => !p.expulsado).length);

  private generation = 0;

  constructor() {
    effect(() => {
      const pin = this.pin();
      untracked(() => this.load(pin));
    });
  }

  label(v: string): string { return labels[v] ?? v; }
  seconds(ms: number): string { return `${((ms || 0) / 1000).toLocaleString('es-PE')} s`; }

  participationText(estado: string): string {
    if (estado === 'SALA_ESPERA') return 'Ya estás en la sala. Espera las indicaciones de tu docente.';
    if (estado === 'FINALIZADA') return 'La sala terminó. Consulta la clasificación.';
    return 'Tu participación está registrada. Sigue las indicaciones de tu docente.';
  }

  search(event: SubmitEvent): void {
    event.preventDefault();
    const pin = new FormData(event.target as HTMLFormElement).get('pin');
    this.router.navigateByUrl(`/salas?pin=${pin}`);
  }

  reload(): void { this.load(this.pin()); }

  async join(s: any): Promise<void> {
    await this.api.request(`/salas/${s.id}/participaciones`, { method: 'POST', body: { estudianteId: this.session.session()!.usuarioId, pin: this.pin() } });
    this.reload();
  }

  private async load(pin: string): Promise<void> {
    const current = ++this.generation;
    if (!pin || !this.session.session() || this.session.session()?.rol !== 'ESTUDIANTE_UTP') { this.sala.set(null); return; }
    const sala = await this.api.request<any>(`/salas/por-pin?pin=${encodeURIComponent(pin)}`);
    const [participants, ranking] = await Promise.all([
      this.api.request<any[]>(`/salas/${sala.id}/participaciones`),
      this.api.request<any[]>(`/salas/${sala.id}/ranking`),
    ]);
    if (current !== this.generation) return;
    this.sala.set(sala);
    this.participants.set(participants);
    this.ranking.set(ranking);
  }
}
