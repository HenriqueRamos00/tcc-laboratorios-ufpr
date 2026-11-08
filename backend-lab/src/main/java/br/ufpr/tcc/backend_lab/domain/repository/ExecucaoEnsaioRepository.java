package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecucaoEnsaioRepository extends JpaRepository<ExecucaoEnsaio, Long> {
    boolean existsByOrcamentoId(Long orcamentoId);

    @Query("""
            select e.orcamento.id as orcamentoId, e.status as status
            from ExecucaoEnsaio e where e.orcamento.id in :ids
            """)
    List<Andamento> consultarAndamentos(@Param("ids") Collection<Long> ids);

    interface Andamento {
        Long getOrcamentoId();
        StatusEnsaio getStatus();
    }

    @EntityGraph(attributePaths = {"amostra", "amostra.orcamento", "amostra.orcamento.empresa", "responsavel"})
    @Query("""
            select e from ExecucaoEnsaio e
            where (lower(e.codigo) like lower(concat('%', :q, '%')) escape '!'
                or lower(e.tipo) like lower(concat('%', :q, '%')) escape '!'
                or lower(e.amostra.codigo) like lower(concat('%', :q, '%')) escape '!'
                or lower(e.amostra.orcamento.empresa.legalName) like lower(concat('%', :q, '%')) escape '!')
            """)
    Page<ExecucaoEnsaio> pesquisar(@Param("q") String busca, Pageable pageable);

    @EntityGraph(attributePaths = {"amostra", "amostra.orcamento", "amostra.orcamento.empresa",
            "amostra.equipamentoAnalisado", "orcamento", "responsavel"})
    @Query("select e from ExecucaoEnsaio e where e.id = :id")
    Optional<ExecucaoEnsaio> buscarCompleto(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExecucaoEnsaio e where e.id = :id")
    Optional<ExecucaoEnsaio> buscarParaEditar(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExecucaoEnsaio e where e.id = :id")
    Optional<ExecucaoEnsaio> buscarParaPrepararRelatorio(@Param("id") Long id);
}
