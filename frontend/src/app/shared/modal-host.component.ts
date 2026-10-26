import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { IconDirective } from '../core/icon.directive';
import { ModalService } from '../core/ui.service';

// Un único <dialog> nativo para todas las ventanas: conserva foco y tecla Escape.
@Component({
  selector: 'app-modal-host',
  imports: [NgComponentOutlet, IconDirective],
  template: `
    <dialog #dialog aria-labelledby="modal-title" (close)="modal.close()">
      @if (modal.current(); as m) {
        <div class="modal-heading">
          <h2 id="modal-title">{{ m.title }}</h2>
          <button class="icon-button" type="button" aria-label="Cerrar ventana" (click)="modal.close()"><svg appIcon="close"></svg></button>
        </div>
        <ng-container [ngComponentOutlet]="m.component" [ngComponentOutletInputs]="m.inputs" />
      }
    </dialog>
  `,
})
export class ModalHostComponent {
  readonly modal = inject(ModalService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      const open = !!this.modal.current();
      if (open && !el.open) el.showModal();
      if (!open && el.open) el.close();
    });
  }
}
