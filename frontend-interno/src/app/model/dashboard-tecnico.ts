import { ReportValidationStatus } from './relatorio';

export interface DashboardTecnico {
  readonly testsInExecution: number;
  readonly testsAwaitingApproval: number;
  readonly testsFinished: number;
  readonly pendingReportsTotal: number;
  readonly pendingReports: readonly DashboardTecnicoReport[];
}

export interface DashboardTecnicoReport {
  readonly id: string;
  readonly companyName: string;
  readonly type: string;
  readonly validationStatus: ReportValidationStatus;
}
