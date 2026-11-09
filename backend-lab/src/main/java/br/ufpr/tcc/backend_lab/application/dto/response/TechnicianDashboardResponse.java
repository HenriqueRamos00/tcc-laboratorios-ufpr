package br.ufpr.tcc.backend_lab.application.dto.response;

import java.util.List;

public record TechnicianDashboardResponse(
        long testsInExecution,
        long testsAwaitingApproval,
        long testsFinished,
        long pendingReportsTotal,
        List<PendingReport> pendingReports) {

    public TechnicianDashboardResponse {
        pendingReports = List.copyOf(pendingReports);
    }

    public record PendingReport(
            String id,
            String companyName,
            String type,
            String validationStatus) { }
}
