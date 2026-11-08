import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { classificationLabel, formatCivilDate } from '@/app/model/ensaio-presentation';
import {
  formatReportDate,
  measurementResultLabel,
  publicationStatusClass,
  publicationStatusLabel,
  reportEventDateLabel,
  technicalStatusLabel,
  validationStatusClass,
  validationStatusLabel,
} from '@/app/model/relatorio-presentation';
import { ReportDetail } from '@/app/model/relatorio';
import { ActionButtonComponent } from '@shared/components/action-button/action-button.component';

@Component({
  selector: 'app-report-detail',
  standalone: true,
  imports: [ActionButtonComponent, MatButtonModule, MatIconModule],
  templateUrl: './report-detail.component.html',
  styleUrl: './report-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportDetailComponent {
  readonly report = input.required<ReportDetail>();
  readonly saving = input(false);
  readonly validationConflict = input(false);
  readonly validationMessage = input<string | null>(null);
  readonly validationError = input<string | null>(null);
  readonly publishing = input(false);
  readonly publicationConflict = input(false);
  readonly publicationMessage = input<string | null>(null);
  readonly publicationError = input<string | null>(null);
  readonly pdfLoading = input(false);
  readonly pdfError = input<string | null>(null);

  readonly validate = output<void>();
  readonly publish = output<void>();
  readonly retry = output<void>();
  readonly openPdf = output<void>();
  readonly downloadPdf = output<void>();

  statusLabel = validationStatusLabel;
  statusClass = validationStatusClass;
  publicationLabel = publicationStatusLabel;
  publicationClass = publicationStatusClass;
  testStatusLabel = technicalStatusLabel;
  resultLabel = measurementResultLabel;
  classificationLabel = classificationLabel;
  dateLabel = formatReportDate;
  eventDateLabel = reportEventDateLabel;
  civilDateLabel = formatCivilDate;
}
