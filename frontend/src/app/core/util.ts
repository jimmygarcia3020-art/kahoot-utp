export const labels: Record<string, string> = {
  BASICO: 'Básico', INTERMEDIO: 'Intermedio', AVANZADO: 'Avanzado',
  SELECCION_MULTIPLE: 'Selección múltiple', ORDENAR_BLOQUES: 'Ordenar bloques',
  PREDECIR_SALIDA: 'Predecir la salida', SELECCIONAR_FRAGMENTO: 'Seleccionar código',
  BORRADOR: 'Borrador', LISTO: 'Listo para usar', SALA_ESPERA: 'Sala de espera',
  EN_CURSO: 'En curso', PAUSADA: 'Pausada', FINALIZADA: 'Finalizada',
  PROPIA: 'Pregunta propia', COPIA: 'Copia privada', INSTITUCIONAL: 'Institucional',
  ESTUDIANTE_UTP: 'Estudiante UTP', DOCENTE_UTP: 'Docente UTP', USUARIO_EXTERNO: 'Autodidacta',
};

export const badgeInfo: Record<string, [string, string, string]> = {
  PRIMER_PASO: ['Primer paso', 'Completa tu primer micro-reto.', 'flag'],
  EXPLORADOR: ['Explorador', 'Completa un reto de cada curso.', 'compass'],
  DOMINIO_BASICO: ['Dominio básico', 'Completa los retos básicos de un tema.', 'shield'],
  PERSEVERANTE: ['Perseverante', 'Resuelve un reto tras tres o más intentos.', 'flame'],
  CURSO_COMPLETADO: ['Curso completado', 'Completa todos los retos de un curso.', 'award'],
};

export interface Preferences {
  tema: string;
  tamanoTexto: string;
  reduccionMovimiento: boolean;
  sonidoActivado: boolean;
  modoDaltonismo: boolean;
}

export const defaultPreferences: Preferences = {
  tema: 'NORMAL', tamanoTexto: 'NORMAL', reduccionMovimiento: false, sonidoActivado: true, modoDaltonismo: false,
};

export interface AuthSession {
  token: string;
  tipoToken: string;
  expiraEnMs: number;
  expiresAt: number;
  usuarioId: number;
  correo: string;
  nombres: string;
  apellidos: string;
  rol: string;
  genero?: string | null;
  fechaNacimiento?: string | null;
}

export function percent(done: number, total: number): number {
  return total ? Math.min(100, Math.round(done / total * 100)) : 0;
}

export function formatTime(ms: number | null | undefined): string {
  return `${((ms || 0) / 1000).toLocaleString('es-PE', { maximumFractionDigits: 1 })} s`;
}

export function readStored<T>(storage: Storage, key: string, fallback: T | null = null): T | null {
  try { return (JSON.parse(storage.getItem(key) as string) as T | null) ?? fallback; } catch { return fallback; }
}

export function validSession(session: AuthSession | null, now = Date.now()): AuthSession | null {
  return session?.token && session?.usuarioId && session.expiresAt > now ? session : null;
}

export function needsProfile(session: AuthSession | null): boolean {
  return !!session && (!session.genero || !session.fechaNacimiento);
}

// Fecha máxima aceptada por el backend (@Past): ayer, en la hora de Lima.
export function maxBirthDate(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date(now.getTime() - 86400000));
}

export function evaluationPayload(reto: any, answer: any, start: number, usoAyuda = false) {
  return {
    microRetoId: reto.id,
    alternativaId: reto.tipo === 'ORDENAR_BLOQUES' ? null : Number(answer),
    ordenBloques: reto.tipo === 'ORDENAR_BLOQUES' ? answer.map(Number) : null,
    usoAyuda,
    tiempoMs: Math.max(0, Date.now() - start),
  };
}

export interface CourseStyle { key: string; icon: string; subtitle: string; description: string; }

export function courseStyle(name = ''): CourseStyle {
  if (/python/i.test(name)) return { key: 'python', icon: 'code', subtitle: 'Dale vida a tus ideas.', description: 'Explora expresiones, lógica y el lenguaje de las posibilidades.' };
  if (/datos/i.test(name)) return { key: 'database', icon: 'database', subtitle: 'Conecta la información.', description: 'Comprende tus datos y encuentra respuestas con SQL.' };
  return { key: 'algorithms', icon: 'workflow', subtitle: 'Piensa. Resuelve. Crea.', description: 'Construye las bases de la lógica y resuelve problemas paso a paso.' };
}

export function formDataObject(form: HTMLFormElement): Record<string, string> {
  return Object.fromEntries(new FormData(form)) as Record<string, string>;
}
