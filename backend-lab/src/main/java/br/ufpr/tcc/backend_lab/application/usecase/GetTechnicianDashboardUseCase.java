package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.TechnicianDashboardResponse;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.TechnicianDashboardConsultaRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTechnicianDashboardUseCase {
    private static final int MAX_PENDING_REPORTS = 6;
    private static final Set<StatusEnsaio> DASHBOARD_STATUSES = Set.of(
            StatusEnsaio.INCOMPLETE,
            StatusEnsaio.COMPLETE,
            StatusEnsaio.FINALIZED,
            StatusEnsaio.DRAFTING_REPORT,
            StatusEnsaio.REPORT_PUBLISHED);

    private final TechnicianDashboardConsultaRepository dashboard;

    public GetTechnicianDashboardUseCase(
            TechnicianDashboardConsultaRepository dashboard) {
        this.dashboard = dashboard;
    }

    @Transactional(readOnly = true)
    public TechnicianDashboardResponse execute() {
        Map<StatusEnsaio, Long> totalsByStatus = new EnumMap<>(StatusEnsaio.class);
        for (TechnicianDashboardConsultaRepository.StatusCount result :
                dashboard.contarEnsaiosPorStatus(DASHBOARD_STATUSES)) {
            totalsByStatus.put(result.status(), result.total());
        }

        long pendingReportsTotal = dashboard.contarRelatoriosPendentes(
                StatusValidacaoRelatorio.PENDING_VALIDATION);
        List<TechnicianDashboardResponse.PendingReport> pendingReports = dashboard
                .listarRelatoriosPendentes(StatusValidacaoRelatorio.PENDING_VALIDATION, MAX_PENDING_REPORTS)
                .stream()
                .map(report -> new TechnicianDashboardResponse.PendingReport(
                        report.id().toString(),
                        report.companyName(),
                        report.type(),
                        report.validationStatus().codigo()))
                .toList();

        return new TechnicianDashboardResponse(
                total(totalsByStatus, StatusEnsaio.INCOMPLETE),
                total(totalsByStatus, StatusEnsaio.COMPLETE),
                total(totalsByStatus, StatusEnsaio.FINALIZED)
                        + total(totalsByStatus, StatusEnsaio.DRAFTING_REPORT)
                        + total(totalsByStatus, StatusEnsaio.REPORT_PUBLISHED),
                pendingReportsTotal,
                pendingReports);
    }

    private long total(Map<StatusEnsaio, Long> totalsByStatus, StatusEnsaio status) {
        return totalsByStatus.getOrDefault(status, 0L);
    }
}
