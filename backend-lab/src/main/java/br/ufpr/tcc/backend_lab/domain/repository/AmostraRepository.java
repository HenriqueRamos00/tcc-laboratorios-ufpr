package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AmostraRepository extends JpaRepository<Amostra, Long> {
    @EntityGraph(attributePaths = {"orcamento", "orcamento.empresa", "equipamentoAnalisado"})
    @Query(value = """
            select a from Amostra a
            join a.orcamento o join o.empresa empresa left join a.equipamentoAnalisado e
            where (:quoteId is null or o.id = :quoteId)
                and (lower(a.codigo) like lower(concat('%', :q, '%')) escape '!'
                or lower(a.descricao) like lower(concat('%', :q, '%')) escape '!'
                or lower(o.code) like lower(concat('%', :q, '%')) escape '!'
                or lower(empresa.legalName) like lower(concat('%', :q, '%')) escape '!')
            """,
            countQuery = """
            select count(a) from Amostra a
            join a.orcamento o join o.empresa empresa left join a.equipamentoAnalisado e
            where (:quoteId is null or o.id = :quoteId)
                and (lower(a.codigo) like lower(concat('%', :q, '%')) escape '!'
                or lower(a.descricao) like lower(concat('%', :q, '%')) escape '!'
                or lower(o.code) like lower(concat('%', :q, '%')) escape '!'
                or lower(empresa.legalName) like lower(concat('%', :q, '%')) escape '!')
            """)
    Page<Amostra> pesquisar(@Param("q") String busca, @Param("quoteId") Long quoteId, Pageable pageable);

    boolean existsByCodigo(String codigo);
}
