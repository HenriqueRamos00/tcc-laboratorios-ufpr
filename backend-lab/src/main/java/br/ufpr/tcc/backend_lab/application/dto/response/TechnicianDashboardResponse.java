package br.ufpr.tcc.backend_lab.application.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TechnicianDashboardResponse(
        long testsInExecution,
        long testsAwaitingApproval,
        long testsFinished,
        long pendingReportsTotal,
        List<PendingReport> pendingReports,
        ReportAnalytics reportAnalytics) {

    public TechnicianDashboardResponse {
        pendingReports = List.copyOf(pendingReports);
    }

    public record PendingReport(
            String id,
            String companyName,
            String type,
            String validationStatus) { }

    public record ReportAnalytics(
            LocalDate referenceDate,
            List<MonthlyReports> monthlyReports,
            PendingReportAge pendingReportAge) {
        public ReportAnalytics {
            monthlyReports = List.copyOf(monthlyReports);
        }
    }

    public record MonthlyReports(String month, long validated, long published) { }

    public record PendingReportAge(
            long upTo7Days,
            long from8To15Days,
            long over15Days,
            LocalDate oldestCreatedOn) { }
}
