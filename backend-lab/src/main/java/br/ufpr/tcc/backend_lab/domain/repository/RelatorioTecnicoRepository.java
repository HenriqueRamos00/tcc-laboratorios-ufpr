package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RelatorioTecnicoRepository extends JpaRepository<RelatorioTecnico, Long> {
    interface ContagemPublicacoesEquipamento {
        Long getEquipmentId();
        Long getAnalysisCount();
    }

    // Conta execuções distintas com relatório publicado e PDF válido por equipamento,
    // considerando somente as empresas às quais o cliente tem acesso.
    @Query("""
            select r.validatedEquipmentId as equipmentId, count(distinct r.execucao.id) as analysisCount
            from RelatorioTecnico r
            where r.validatedEquipmentId in :equipmentIds
              and r.validatedCompany.ativo = true
              and r.validatedCompany.id in :companyIds
              and r.validationStatus = :validated and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id and d.pdfValido = true)
            group by r.validatedEquipmentId
            """)
    List<ContagemPublicacoesEquipamento> contarExecucoesPublicadasPorEquipamento(
            @Param("equipmentIds") Collection<Long> equipmentIds,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("validated") StatusValidacaoRelatorio validated);

    // Retorna a publicação mais recente por equipamento, considerando apenas relatórios validados,
    // publicados e com PDF válido. Em empate de data, o maior ID vence.
    @Query("""
            select r from RelatorioTecnico r
            where r.validatedEquipmentId in :equipmentIds
              and r.validatedCompany.ativo = true
              and r.validatedCompany.id in :companyIds
              and r.validationStatus = :validated and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id and d.pdfValido = true)
              and not exists (
                  select newer.id from RelatorioTecnico newer
                  where newer.validatedEquipmentId = r.validatedEquipmentId
                    and newer.validatedCompany.ativo = true
                    and newer.validatedCompany.id in :companyIds
                    and newer.validationStatus = :validated and newer.published = true
                    and exists (select d.id from DocumentoRelatorio d where d.id = newer.id and d.pdfValido = true)
                    and ((r.publishedAt is null and newer.publishedAt is not null)
                         or (r.publishedAt is not null and newer.publishedAt is not null and newer.publishedAt > r.publishedAt)
                         or ((newer.publishedAt = r.publishedAt
                              or (newer.publishedAt is null and r.publishedAt is null)) and newer.id > r.id))
              )
            """)
    List<RelatorioTecnico> buscarPublicacaoMaisRecentePorEquipamento(
            @Param("equipmentIds") Collection<Long> equipmentIds,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("validated") StatusValidacaoRelatorio validated);

    // No histórico do equipamento, mantém a publicação mais recente de cada execução. Empresa e
    // orçamento são filtros opcionais; em empate de data, o maior ID vence.
    @Query(value = """
            select r from RelatorioTecnico r
            where r.validatedEquipmentId = :equipmentId
              and r.validatedCompany.ativo = true
              and r.validatedCompany.id in :companyIds
              and (:companyId is null or r.validatedCompany.id = :companyId)
              and (:quoteId is null or r.validatedQuote.id = :quoteId)
              and r.validationStatus = :validated and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id and d.pdfValido = true)
              and not exists (
                  select newer.id from RelatorioTecnico newer
                  where newer.execucao.id = r.execucao.id
                    and newer.validatedEquipmentId = r.validatedEquipmentId
                    and newer.validatedCompany.ativo = true
                    and newer.validatedCompany.id in :companyIds
                    and (:companyId is null or newer.validatedCompany.id = :companyId)
                    and (:quoteId is null or newer.validatedQuote.id = :quoteId)
                    and newer.validationStatus = :validated and newer.published = true
                    and exists (select d.id from DocumentoRelatorio d where d.id = newer.id and d.pdfValido = true)
                    and ((r.publishedAt is null and newer.publishedAt is not null)
                         or (r.publishedAt is not null and newer.publishedAt is not null and newer.publishedAt > r.publishedAt)
                         or ((newer.publishedAt = r.publishedAt
                              or (newer.publishedAt is null and r.publishedAt is null)) and newer.id > r.id))
              )
            """,
            countQuery = """
            select count(r) from RelatorioTecnico r
            where r.validatedEquipmentId = :equipmentId
              and r.validatedCompany.ativo = true
              and r.validatedCompany.id in :companyIds
              and (:companyId is null or r.validatedCompany.id = :companyId)
              and (:quoteId is null or r.validatedQuote.id = :quoteId)
              and r.validationStatus = :validated and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id and d.pdfValido = true)
              and not exists (
                  select newer.id from RelatorioTecnico newer
                  where newer.execucao.id = r.execucao.id
                    and newer.validatedEquipmentId = r.validatedEquipmentId
                    and newer.validatedCompany.ativo = true
                    and newer.validatedCompany.id in :companyIds
                    and (:companyId is null or newer.validatedCompany.id = :companyId)
                    and (:quoteId is null or newer.validatedQuote.id = :quoteId)
                    and newer.validationStatus = :validated and newer.published = true
                    and exists (select d.id from DocumentoRelatorio d where d.id = newer.id and d.pdfValido = true)
                    and ((r.publishedAt is null and newer.publishedAt is not null)
                         or (r.publishedAt is not null and newer.publishedAt is not null and newer.publishedAt > r.publishedAt)
                         or ((newer.publishedAt = r.publishedAt
                              or (newer.publishedAt is null and r.publishedAt is null)) and newer.id > r.id))
              )
            """)
    Page<RelatorioTecnico> buscarHistoricoCliente(Long equipmentId, Collection<Long> companyIds,
            Long companyId, Long quoteId, StatusValidacaoRelatorio validated, Pageable pageable);

    @Query("""
            select case when count(r) > 0 then true else false end
            from RelatorioTecnico r
            where r.validatedEquipmentId = :equipmentId
              and r.validationStatus = :validated and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id and d.pdfValido = true)
            """)
    boolean existePublicacaoDisponivelDoEquipamento(@Param("equipmentId") Long equipmentId,
            @Param("validated") StatusValidacaoRelatorio validated);

    // Aplica a disponibilidade do equipamento somente às empresas autorizadas para o cliente.
    @Query("""
            select case when count(r) > 0 then true else false end
            from RelatorioTecnico r
            where r.validatedEquipmentId = :equipmentId
              and r.validatedCompany.ativo = true and r.validatedCompany.id in :companyIds
              and r.validationStatus = :validated and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id and d.pdfValido = true)
            """)
    boolean existePublicacaoAutorizadaDoEquipamento(@Param("equipmentId") Long equipmentId,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("validated") StatusValidacaoRelatorio validated);

    @EntityGraph(attributePaths = {"execucao", "execucao.amostra", "execucao.orcamento",
            "execucao.orcamento.empresa"})
    List<RelatorioTecnico> findByExecucaoIdOrderByIdAsc(Long execucaoId);

    boolean existsByExecucaoIdAndPublishedTrue(Long execucaoId);

    boolean existsByValidatedQuoteIdAndPublishedTrue(Long quoteId);

    // Lista relatórios publicados da execução que já têm documento associado e vínculos comerciais.
    @EntityGraph(attributePaths = {"validatedCompany", "validatedQuote"})
    @Query("""
            select distinct r from RelatorioTecnico r
            where r.execucao.id = :execucaoId
              and r.validationStatus = :validated
              and r.published = true
              and r.validatedQuote is not null
              and r.validatedCompany is not null
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id)
            order by r.id asc
            """)
    List<RelatorioTecnico> buscarPublicadosDaExecucao(@Param("execucaoId") Long execucaoId,
            @Param("validated") StatusValidacaoRelatorio validated);

    // Restringe a consulta às empresas autorizadas e carrega os dados exibidos no portal do cliente.
    @EntityGraph(attributePaths = {"validatedCompany", "validatedQuote", "validatedSample"})
    @Query("""
            select distinct r from RelatorioTecnico r
            join r.validatedCompany empresa
            where r.validationStatus = :validated
              and r.published = true
              and empresa.ativo = true
              and empresa.id in :empresaIds
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id)
            order by r.publishedAt desc, r.id desc
            """)
    List<RelatorioTecnico> buscarPublicadosDasEmpresas(@Param("empresaIds") Collection<Long> empresaIds,
            @Param("validated") StatusValidacaoRelatorio validated);

    // Lista os relatórios publicados de um orçamento específico, dentro da empresa informada.
    @EntityGraph(attributePaths = {"validatedCompany", "validatedQuote", "validatedSample"})
    @Query("""
            select distinct r from RelatorioTecnico r
            join r.validatedQuote o
            join r.validatedCompany empresa
            where o.id = :orcamentoId
              and empresa.id = :empresaId
              and r.validationStatus = :validated
              and r.published = true
              and exists (select d.id from DocumentoRelatorio d where d.id = r.id)
            order by r.publishedAt desc, r.id desc
            """)
    List<RelatorioTecnico> buscarPublicadosDoOrcamento(@Param("orcamentoId") Long orcamentoId,
            @Param("empresaId") Long empresaId,
            @Param("validated") StatusValidacaoRelatorio validated);

    // Antes da validação, busca nos dados atuais do ensaio; depois, usa os termos preservados
    // na validação para que alterações posteriores não mudem a pesquisa do relatório.
    @EntityGraph(attributePaths = {"execucao", "execucao.amostra", "execucao.orcamento",
            "execucao.orcamento.empresa"})
    @Query(value = """
            select distinct r from RelatorioTecnico r
            join r.execucao e
            join e.amostra a
            join e.orcamento o
            join o.empresa empresa
            where (:validationStatus is null or r.validationStatus = :validationStatus)
              and (:published is null or r.published = :published)
              and (:search is null or :search = ''
                   or lower(cast(r.id as string)) like lower(concat('%', :search, '%')) escape '!'
                   or (r.validationStatus = :validatedStatus
                       and lower(coalesce(r.validatedSearch, '')) like lower(concat('%', :search, '%')) escape '!')
                   or (r.validationStatus <> :validatedStatus
                       and (lower(e.codigo) like lower(concat('%', :search, '%')) escape '!'
                            or lower(a.codigo) like lower(concat('%', :search, '%')) escape '!'
                            or lower(empresa.legalName) like lower(concat('%', :search, '%')) escape '!'
                            or lower(e.tipo) like lower(concat('%', :search, '%')) escape '!'
                            or lower(coalesce(e.tipoAnalise, '')) like lower(concat('%', :search, '%')) escape '!')))
            """,
            countQuery = """
            select count(r) from RelatorioTecnico r
            join r.execucao e
            join e.amostra a
            join e.orcamento o
            join o.empresa empresa
            where (:validationStatus is null or r.validationStatus = :validationStatus)
              and (:published is null or r.published = :published)
              and (:search is null or :search = ''
                   or lower(cast(r.id as string)) like lower(concat('%', :search, '%')) escape '!'
                   or (r.validationStatus = :validatedStatus
                       and lower(coalesce(r.validatedSearch, '')) like lower(concat('%', :search, '%')) escape '!')
                   or (r.validationStatus <> :validatedStatus
                       and (lower(e.codigo) like lower(concat('%', :search, '%')) escape '!'
                            or lower(a.codigo) like lower(concat('%', :search, '%')) escape '!'
                            or lower(empresa.legalName) like lower(concat('%', :search, '%')) escape '!'
                            or lower(e.tipo) like lower(concat('%', :search, '%')) escape '!'
                            or lower(coalesce(e.tipoAnalise, '')) like lower(concat('%', :search, '%')) escape '!')))
            """)
    Page<RelatorioTecnico> pesquisar(@Param("search") String search,
            @Param("validationStatus") StatusValidacaoRelatorio validationStatus,
            @Param("published") Boolean published,
            @Param("validatedStatus") StatusValidacaoRelatorio validatedStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"execucao", "execucao.amostra", "execucao.amostra.equipamentoAnalisado",
            "execucao.orcamento", "execucao.orcamento.empresa", "execucao.responsavel",
            "execucao.instrumentos", "execucao.medicoes", "validatedBy"})
    @Query("select distinct r from RelatorioTecnico r where r.id = :id")
    Optional<RelatorioTecnico> buscarDetalhe(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RelatorioTecnico r where r.id = :id")
    Optional<RelatorioTecnico> buscarParaValidacao(@Param("id") Long id);
}
