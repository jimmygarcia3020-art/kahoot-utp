import { Injectable, Type, signal } from '@angular/core';

// Aviso breve (toast) que se oculta solo tras 5,5 s.
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal('');
  readonly visible = signal(false);
  private timer: ReturnType<typeof setTimeout> | undefined;

  show(message: string): void {
    this.message.set(message);
    this.visible.set(true);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.visible.set(false), 5500);
  }
}

export interface ModalState {
  title: string;
  component: Type<unknown>;
  inputs: Record<string, unknown>;
}

// Ventana modal genérica: el contenido es un componente que se cierra con close().
@Injectable({ providedIn: 'root' })
export class ModalService {
  readonly current = signal<ModalState | null>(null);

  open(title: string, component: Type<unknown>, inputs: Record<string, unknown> = {}): void {
    this.current.set({ title, component, inputs });
  }

  close(): void { this.current.set(null); }
}
