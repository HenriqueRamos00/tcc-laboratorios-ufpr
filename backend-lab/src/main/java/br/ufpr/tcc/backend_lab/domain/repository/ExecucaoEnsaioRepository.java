package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecucaoEnsaioRepository extends JpaRepository<ExecucaoEnsaio, Long> {
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
}
