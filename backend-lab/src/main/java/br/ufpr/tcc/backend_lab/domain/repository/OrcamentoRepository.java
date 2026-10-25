package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    long countByStatus(StatusComercialOrcamento status);

    @Query("select sum(orcamento.totalPrice) from Orcamento orcamento")
    BigDecimal sumTotalPrice();

    @EntityGraph(attributePaths = {"empresa", "items"})
    @Query("select orcamento from Orcamento orcamento where orcamento.id = :id")
    Optional<Orcamento> buscarDetalhePorId(Long id);

    @EntityGraph(attributePaths = "empresa")
    @Query(
            value =
                    """
                    select orcamento
                    from Orcamento orcamento
                    where (:status is null or orcamento.status = :status)
                      and (
                          :busca is null
                          or lower(orcamento.code) like :busca escape '!'
                          or lower(orcamento.name) like :busca escape '!'
                          or lower(orcamento.description) like :busca escape '!'
                          or lower(orcamento.empresa.legalName) like :busca escape '!'
                      )
                    """,
            countQuery =
                    """
                    select count(orcamento)
                    from Orcamento orcamento
                    where (:status is null or orcamento.status = :status)
                      and (
                          :busca is null
                          or lower(orcamento.code) like :busca escape '!'
                          or lower(orcamento.name) like :busca escape '!'
                          or lower(orcamento.description) like :busca escape '!'
                          or lower(orcamento.empresa.legalName) like :busca escape '!'
                      )
                    """)
    Page<Orcamento> pesquisarInternos(
            String busca, StatusComercialOrcamento status, Pageable pageable);

    @Query(
            """
            select orcamento
            from Orcamento orcamento
            where orcamento.empresa.ativo = true
              and exists (
                  select cliente.id
                  from Cliente cliente
                  join cliente.empresas empresa
                  where cliente.usuario.id = :usuarioId
                    and empresa.id = orcamento.empresa.id
              )
            order by orcamento.createdDate desc, orcamento.id
            """)
    List<Orcamento> listarAcessiveisPorUsuario(Long usuarioId);
}
