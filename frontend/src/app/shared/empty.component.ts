import { Component, input } from '@angular/core';
import { IconDirective } from '../core/icon.directive';

@Component({
  selector: 'app-empty',
  imports: [IconDirective],
  template: `<div class="empty"><svg appIcon="compass"></svg><h3>{{ title() }}</h3><p>{{ description() }}</p><ng-content /></div>`,
})
export class EmptyComponent {
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
