package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
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

    public long contarRelatoriosPendentes(StatusValidacaoRelatorio validationStatus) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM relatorios_tecnicos
                WHERE validation_status = :validationStatus
                """,
                new MapSqlParameterSource("validationStatus", validationStatus.name()),
                Long.class);
        return total == null ? 0 : total;
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

    public record PendingReport(
            Long id,
            String companyName,
            String type,
            StatusValidacaoRelatorio validationStatus) { }
}
