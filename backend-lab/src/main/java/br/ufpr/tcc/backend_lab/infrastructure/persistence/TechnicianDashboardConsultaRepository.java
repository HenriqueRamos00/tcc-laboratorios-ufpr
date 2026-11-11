package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TechnicianDashboardConsultaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<StatusCount> contarEnsaiosPorStatus(Collection<StatusEnsaio> statuses) {
        Set<String> statusCodes = statuses.stream().map(StatusEnsaio::codigo)
                .collect(Collectors.toUnmodifiableSet());
        return jdbc.query("""
                SELECT status, COUNT(*) AS total
                FROM execucoes_ensaios
                WHERE status IN (:statuses)
                GROUP BY status
                """,
                new MapSqlParameterSource("statuses", statusCodes),
                (resultSet, rowNum) -> new StatusCount(
                        StatusEnsaio.deCodigo(resultSet.getString("status")),
                        resultSet.getLong("total")));
    }

    public PendingReportAgeCount contarIdadeRelatoriosPendentes(
            StatusValidacaoRelatorio validationStatus,
            OffsetDateTime sevenDaysAgo, OffsetDateTime fifteenDaysAgo) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN created_at >= :sevenDaysAgo THEN 1 ELSE 0 END) AS up_to_7,
                       SUM(CASE WHEN created_at < :sevenDaysAgo AND created_at >= :fifteenDaysAgo
                           THEN 1 ELSE 0 END) AS from_8_to_15,
                       SUM(CASE WHEN created_at < :fifteenDaysAgo THEN 1 ELSE 0 END) AS over_15,
                       MIN(created_at) AS oldest_created_at
                FROM relatorios_tecnicos
                WHERE validation_status = :validationStatus
                """,
                new MapSqlParameterSource("validationStatus", validationStatus.name())
                        .addValue("sevenDaysAgo", sevenDaysAgo)
                        .addValue("fifteenDaysAgo", fifteenDaysAgo),
                (row, index) -> new PendingReportAgeCount(
                        row.getLong("total"), row.getLong("up_to_7"),
                        row.getLong("from_8_to_15"), row.getLong("over_15"),
                        row.getObject("oldest_created_at", OffsetDateTime.class)));
    }

    public List<MonthlyReportCount> contarRelatoriosPorMes(
            List<OffsetDateTime> monthStarts, OffsetDateTime until) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("validatedStatus", StatusValidacaoRelatorio.VALIDATED.name())
                .addValue("since", monthStarts.getFirst())
                .addValue("until", until);
        for (int index = 1; index < monthStarts.size(); index++) {
            parameters.addValue("month" + index, monthStarts.get(index));
        }
        return jdbc.query("""
                SELECT month_index,
                       COUNT(CASE WHEN event_type = 'validated' THEN 1 END) AS validated,
                       COUNT(CASE WHEN event_type = 'published' THEN 1 END) AS published
                FROM (
                    SELECT CASE
                               WHEN event_date >= :month5 THEN 5
                               WHEN event_date >= :month4 THEN 4
                               WHEN event_date >= :month3 THEN 3
                               WHEN event_date >= :month2 THEN 2
                               WHEN event_date >= :month1 THEN 1
                               ELSE 0
                           END AS month_index,
                           event_type
                    FROM (
                        SELECT validated_at AS event_date, 'validated' AS event_type
                        FROM relatorios_tecnicos
                        WHERE validation_status = :validatedStatus
                          AND validated_at >= :since AND validated_at < :until
                        UNION ALL
                        SELECT published_at AS event_date, 'published' AS event_type
                        FROM relatorios_tecnicos
                        WHERE published = TRUE
                          AND published_at >= :since AND published_at < :until
                    ) events
                ) months
                GROUP BY month_index
                ORDER BY month_index
                """, parameters,
                (row, index) -> new MonthlyReportCount(
                        row.getInt("month_index"), row.getLong("validated"), row.getLong("published")));
    }

    public List<PendingReport> listarRelatoriosPendentes(
            StatusValidacaoRelatorio validationStatus, int limit) {
        return jdbc.query("""
                SELECT relatorio.id,
                       empresa.legal_name AS company_name,
                       execucao.tipo AS type,
                       relatorio.validation_status
                FROM relatorios_tecnicos relatorio
                JOIN execucoes_ensaios execucao ON execucao.id = relatorio.execucao_id
                JOIN orcamentos orcamento ON orcamento.id = execucao.orcamento_id
                JOIN empresas empresa ON empresa.id = orcamento.empresa_id
                WHERE relatorio.validation_status = :validationStatus
                ORDER BY relatorio.id DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("validationStatus", validationStatus.name())
                        .addValue("limit", limit),
                (resultSet, rowNum) -> new PendingReport(
                        resultSet.getLong("id"),
                        resultSet.getString("company_name"),
                        resultSet.getString("type"),
                        StatusValidacaoRelatorio.valueOf(resultSet.getString("validation_status"))));
    }

    public record StatusCount(StatusEnsaio status, long total) { }

    public record MonthlyReportCount(int monthIndex, long validated, long published) { }

    public record PendingReportAgeCount(
            long total, long upTo7Days, long from8To15Days, long over15Days,
            OffsetDateTime oldestCreatedAt) { }

    public record PendingReport(
            Long id,
            String companyName,
            String type,
            StatusValidacaoRelatorio validationStatus) { }
}
