import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';

// Catálogo público: cursos, temas y micro-retos (se carga una vez).
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly api = inject(ApiService);
  readonly courses = signal<any[]>([]);
  readonly temas = signal<any[]>([]);
  readonly retos = signal<any[]>([]);
  readonly error = signal('');
  private loaded = false;

  async load(force = false): Promise<void> {
    if (this.loaded && !force) return;
    try {
      const courses = await this.api.request<any[]>('/cursos');
      const groups = await Promise.all(courses.map(async course => {
        const [temas, retos] = await Promise.all([
          this.api.request<any[]>(`/cursos/${course.id}/temas`),
          this.api.request<any[]>(`/micro-retos?cursoId=${course.id}`),
        ]);
        return {
          temas: temas.map(t => ({ ...t, cursoId: course.id })),
          retos: retos.map(r => ({ ...r, cursoId: course.id })),
        };
      }));
      this.courses.set(courses);
      this.temas.set(groups.flatMap(g => g.temas));
      this.retos.set(groups.flatMap(g => g.retos));
      this.loaded = true;
      this.error.set('');
    } catch (error) {
      this.error.set((error as Error).message);
    }
  }

  courseOf(cursoId: number): any { return this.courses().find(c => c.id === cursoId); }
  temaOf(temaId: number): any { return this.temas().find(t => t.id === temaId); }
  retoOf(id: number): any { return this.retos().find(r => r.id === id); }
}
