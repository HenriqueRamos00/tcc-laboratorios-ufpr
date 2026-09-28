import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-placeholder',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './placeholder.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly data = toSignal(this.route.data, { initialValue: this.route.snapshot.data });

  readonly title = computed(() => String(this.data()['pageTitle'] ?? 'Portal Interno'));
  readonly description = computed(() =>
    String(this.data()['pageDescription'] ?? 'Área de trabalho do portal interno.'),
  );
  readonly icon = computed(() => String(this.data()['pageIcon'] ?? 'dashboard'));
}
