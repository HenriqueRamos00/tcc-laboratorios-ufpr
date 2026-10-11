package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    boolean existsByCnpj(String cnpj);

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    @Query(
            """
            select e from Empresa e
            where (:ativo is null or e.ativo = :ativo)
              and (
                  :busca is null
                  or lower(e.legalName) like :busca escape '!'
                  or e.cnpj like :busca escape '!'
                  or lower(e.city) like :busca escape '!'
                  or lower(e.contactName) like :busca escape '!'
                  or lower(e.contactEmail) like :busca escape '!'
              )
            order by e.legalName, e.id
            """)
    List<Empresa> pesquisar(String busca, Boolean ativo);
}
