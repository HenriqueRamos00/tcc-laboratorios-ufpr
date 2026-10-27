package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusOrcamentoTecnico;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusExecucaoEnsaio;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrcamentoTecnicoConsultaRepository {
    private static final String CTE_RESUMO_TECNICO =
            """
            WITH execucoes_resumidas AS (
                SELECT execucao.orcamento_id,
                       COUNT(*) FILTER (WHERE execucao.status <> :cancelledState) AS ativas,
                       BOOL_AND(
                           execucao.status = :reportPublishedState
                           AND NULLIF(TRIM(execucao.report_reference), '') IS NOT NULL
                           AND execucao.published_at IS NOT NULL
                       ) FILTER (WHERE execucao.status <> :cancelledState) AS todas_publicadas,
                       BOOL_OR(execucao.status = :incompleteState) AS tem_incompleta,
                       BOOL_OR(execucao.status = :completeState) AS tem_completa
                FROM execucoes_ensaios execucao
                GROUP BY execucao.orcamento_id
            ), status_tecnico AS (
                SELECT orcamento.id AS orcamento_id,
                       CASE
                           WHEN resumo.orcamento_id IS NULL THEN NULL
                           WHEN resumo.ativas = 0 THEN :summaryCancelled
                           WHEN resumo.todas_publicadas THEN :summaryReportPublished
                           WHEN resumo.tem_incompleta THEN :summaryIncomplete
                           WHEN resumo.tem_completa THEN :summaryComplete
                           ELSE :summaryValidated
                       END AS technical_status
                FROM orcamentos orcamento
                LEFT JOIN execucoes_resumidas resumo
                    ON resumo.orcamento_id = orcamento.id
            )
            """;

    private static final String FROM_E_FILTROS =
            """
            FROM orcamentos orcamento
            JOIN empresas empresa ON empresa.id = orcamento.empresa_id
            JOIN status_tecnico status ON status.orcamento_id = orcamento.id
            WHERE (CAST(:busca AS text) IS NULL
                   OR LOWER(orcamento.code) LIKE :busca ESCAPE '!'
                   OR LOWER(orcamento.name) LIKE :busca ESCAPE '!'
                   OR LOWER(empresa.legal_name) LIKE :busca ESCAPE '!')
              AND (CAST(:inicio AS timestamp with time zone) IS NULL
                   OR orcamento.created_date >= :inicio)
              AND (CAST(:fimExclusivo AS timestamp with time zone) IS NULL
                   OR orcamento.created_date < :fimExclusivo)
              AND (CAST(:status AS text) IS NULL OR status.technical_status = :status)
            """;

    private static final RowMapper<ProjecaoOrcamentoTecnico> MAPEADOR =
            (resultSet, rowNum) ->
                    new ProjecaoOrcamentoTecnico(
                            resultSet.getLong("id"),
                            resultSet.getString("code"),
                            resultSet.getString("name"),
                            resultSet.getString("company_name"),
                            resultSet.getObject("created_date", OffsetDateTime.class),
                            resultSet.getString("technical_status"));

    private final NamedParameterJdbcTemplate jdbc;

    public Page<ProjecaoOrcamentoTecnico> pesquisar(
            String busca,
            String status,
            OffsetDateTime inicio,
            OffsetDateTime fimExclusivo,
            Pageable pageable) {
        var parametros = parametros(busca, status, inicio, fimExclusivo);
        String consulta =
                CTE_RESUMO_TECNICO
                        + "SELECT orcamento.id, orcamento.code, orcamento.name, "
                        + "empresa.legal_name AS company_name, orcamento.created_date, "
                        + "status.technical_status "
                        + FROM_E_FILTROS
                        + " ORDER BY orcamento.created_date DESC, orcamento.id DESC"
                        + " LIMIT :limite OFFSET :deslocamento";
        List<ProjecaoOrcamentoTecnico> items =
                jdbc.query(
                        consulta,
                        parametros
                                .addValue("limite", pageable.getPageSize())
                                .addValue("deslocamento", pageable.getOffset()),
                        MAPEADOR);

        String consultaContagem =
                CTE_RESUMO_TECNICO + "SELECT COUNT(*) " + FROM_E_FILTROS;
        Long total = jdbc.queryForObject(consultaContagem, parametros, Long.class);
        return new PageImpl<>(items, pageable, total == null ? 0 : total);
    }

    public Optional<StatusOrcamentoTecnico> buscarStatus(Long orcamentoId) {
        String consulta =
                CTE_RESUMO_TECNICO
                        + "SELECT status.technical_status FROM status_tecnico status "
                        + "WHERE status.orcamento_id = :orcamentoId";
        var parametros = parametros(null, null, null, null).addValue("orcamentoId", orcamentoId);
        return jdbc.query(
                        consulta,
                        parametros,
                        resultSet -> {
                            if (!resultSet.next()) {
                                return Optional.empty();
                            }
                            String codigo = resultSet.getString("technical_status");
                            return codigo == null
                                    ? Optional.empty()
                                    : StatusOrcamentoTecnico.fromCodigo(codigo);
                        });
    }

    private MapSqlParameterSource parametros(
            String busca,
            String status,
            OffsetDateTime inicio,
            OffsetDateTime fimExclusivo) {
        return new MapSqlParameterSource()
                .addValue("busca", busca, Types.VARCHAR)
                .addValue("status", status, Types.VARCHAR)
                .addValue("inicio", inicio, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("fimExclusivo", fimExclusivo, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("cancelledState", StatusExecucaoEnsaio.CANCELLED.name())
                .addValue("reportPublishedState", StatusExecucaoEnsaio.REPORT_PUBLISHED.name())
                .addValue("incompleteState", StatusExecucaoEnsaio.INCOMPLETE.name())
                .addValue("completeState", StatusExecucaoEnsaio.COMPLETE.name())
                .addValue("summaryCancelled", StatusOrcamentoTecnico.CANCELLED.codigo())
                .addValue("summaryReportPublished", StatusOrcamentoTecnico.REPORT_PUBLISHED.codigo())
                .addValue("summaryIncomplete", StatusOrcamentoTecnico.INCOMPLETE.codigo())
                .addValue("summaryComplete", StatusOrcamentoTecnico.COMPLETE.codigo())
                .addValue("summaryValidated", StatusOrcamentoTecnico.VALIDATED.codigo());
    }

    public record ProjecaoOrcamentoTecnico(
            Long id,
            String code,
            String name,
            String companyName,
            OffsetDateTime createdDate,
            String technicalStatus) {}
}
