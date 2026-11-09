import { ReportValidationStatus } from './relatorio';

export interface DashboardTecnico {
  readonly testsInExecution: number;
  readonly testsAwaitingApproval: number;
  readonly testsFinished: number;
  readonly pendingReportsTotal: number;
  readonly pendingReports: readonly DashboardTecnicoReport[];
  readonly reportAnalytics: DashboardReportAnalytics;
}

export interface DashboardReportAnalytics {
  readonly referenceDate: string;
  readonly monthlyReports: readonly DashboardMonthlyReports[];
  readonly pendingReportAge: DashboardPendingReportAge;
}

export interface DashboardMonthlyReports {
  readonly month: string;
  readonly validated: number;
  readonly published: number;
}

export interface DashboardPendingReportAge {
  readonly upTo7Days: number;
  readonly from8To15Days: number;
  readonly over15Days: number;
  readonly oldestCreatedOn: string | null;
}

export interface DashboardTecnicoReport {
  readonly id: string;
  readonly companyName: string;
  readonly type: string;
  readonly validationStatus: ReportValidationStatus;
}
