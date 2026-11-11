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
    @EntityGraph(attributePaths = {"execucao", "execucao.amostra", "execucao.orcamento",
            "execucao.orcamento.empresa"})
    List<RelatorioTecnico> findByExecucaoIdOrderByIdAsc(Long execucaoId);

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
