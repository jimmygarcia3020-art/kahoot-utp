import { Injectable } from '@angular/core';

export class ApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

export type DemoBackend = (path: string, options?: { method?: string; body?: any }) => Promise<any>;

interface Configuration {
  token: () => string | null;
  refresh: () => Promise<string | null>;
  onUnauthorized: () => void;
}

// Cliente HTTP: mismas rutas /api del mismo origen, JWT y mensajes de error del servidor.
@Injectable({ providedIn: 'root' })
export class ApiService {
  private config: Configuration = { token: () => null, refresh: async () => null, onUnauthorized: () => {} };
  private demo: DemoBackend | null = null;

  configure(config: Configuration): void { this.config = config; }
  useDemo(backend: DemoBackend): void { this.demo = backend; }

  async request<T = any>(path: string, { method = 'GET', body, anonymous = false, retried = false }: { method?: string; body?: unknown; anonymous?: boolean; retried?: boolean } = {}): Promise<T> {
    if (this.demo) return this.demo(path, { method, body });
    const token = anonymous ? null : this.config.token();
    let response: Response;
    try {
      response = await fetch(`/api${path}`, {
        method,
        headers: {
          Accept: 'application/json',
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
        signal: AbortSignal.timeout(12000),
      });
    } catch (error) {
      if ((error as Error).name === 'AbortError') throw error;
      throw new ApiError('No pudimos conectar con AdaptCode. Comprueba que el servidor esté disponible y vuelve a intentar.', 0);
    }
    const raw = await response.text();
    let data: any;
    try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
    if (!response.ok) {
      if (response.status === 401 && token && !retried) {
        // Access token vencido: renovarlo una vez con el refresh token y repetir la petición.
        const renewed = await this.config.refresh();
        if (renewed) return this.request<T>(path, { method, body, retried: true });
      }
      if (response.status === 401 && token) this.config.onUnauthorized();
      const message = typeof data === 'object' && data
        ? (data.error || data.mensaje || Object.values(data).join(' '))
        : `No se pudo completar la solicitud (${response.status}).`;
      throw new ApiError(message, response.status);
    }
    return data as T;
  }
}
