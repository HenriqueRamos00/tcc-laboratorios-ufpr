package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.TechnicianDashboardResponse;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.TechnicianDashboardConsultaRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTechnicianDashboardUseCase {
    private static final int MAX_PENDING_REPORTS = 6;
    private static final int MONTHS_IN_REPORT_WINDOW = 6;
    private static final ZoneId REPORT_TIME_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<StatusEnsaio> DASHBOARD_STATUSES = Set.of(
            StatusEnsaio.INCOMPLETE,
            StatusEnsaio.COMPLETE,
            StatusEnsaio.FINALIZED,
            StatusEnsaio.DRAFTING_REPORT,
            StatusEnsaio.REPORT_PUBLISHED);

    private final TechnicianDashboardConsultaRepository dashboard;
    private final Clock clock;

    public GetTechnicianDashboardUseCase(
            TechnicianDashboardConsultaRepository dashboard, Clock clock) {
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TechnicianDashboardResponse execute() {
        Map<StatusEnsaio, Long> totalsByStatus = new EnumMap<>(StatusEnsaio.class);
        for (TechnicianDashboardConsultaRepository.StatusCount result :
                dashboard.contarEnsaiosPorStatus(DASHBOARD_STATUSES)) {
            totalsByStatus.put(result.status(), result.total());
        }

        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(REPORT_TIME_ZONE);
        var pendingAge = dashboard.contarIdadeRelatoriosPendentes(
                StatusValidacaoRelatorio.PENDING_VALIDATION,
                now.toLocalDate().minusDays(7).atStartOfDay(REPORT_TIME_ZONE).toOffsetDateTime(),
                now.toLocalDate().minusDays(15).atStartOfDay(REPORT_TIME_ZONE).toOffsetDateTime());
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
                pendingAge.total(),
                pendingReports,
                analisesRelatorios(now, pendingAge));
    }

    private TechnicianDashboardResponse.ReportAnalytics analisesRelatorios(
            ZonedDateTime now, TechnicianDashboardConsultaRepository.PendingReportAgeCount pendingAge) {
        YearMonth firstMonth = YearMonth.from(now).minusMonths(MONTHS_IN_REPORT_WINDOW - 1);
        List<OffsetDateTime> monthStarts = IntStream.range(0, MONTHS_IN_REPORT_WINDOW)
                .mapToObj(index -> firstMonth.plusMonths(index).atDay(1)
                        .atStartOfDay(REPORT_TIME_ZONE).toOffsetDateTime())
                .toList();
        var counts = dashboard.contarRelatoriosPorMes(monthStarts, now.toOffsetDateTime()).stream()
                .collect(Collectors.toMap(
                        TechnicianDashboardConsultaRepository.MonthlyReportCount::monthIndex,
                        Function.identity()));
        var monthlyReports = IntStream.range(0, MONTHS_IN_REPORT_WINDOW)
                .mapToObj(index -> {
                    var count = counts.get(index);
                    return new TechnicianDashboardResponse.MonthlyReports(
                            firstMonth.plusMonths(index).toString(),
                            count == null ? 0 : count.validated(),
                            count == null ? 0 : count.published());
                })
                .toList();
        var ages = new TechnicianDashboardResponse.PendingReportAge(
                pendingAge.upTo7Days(), pendingAge.from8To15Days(), pendingAge.over15Days(),
                pendingAge.oldestCreatedAt() == null ? null
                        : pendingAge.oldestCreatedAt().atZoneSameInstant(REPORT_TIME_ZONE).toLocalDate());
        return new TechnicianDashboardResponse.ReportAnalytics(now.toLocalDate(), monthlyReports, ages);
    }

    private long total(Map<StatusEnsaio, Long> totalsByStatus, StatusEnsaio status) {
        return totalsByStatus.getOrDefault(status, 0L);
    }
}
