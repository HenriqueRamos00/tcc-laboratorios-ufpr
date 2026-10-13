package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByUsuarioId(Long id);

    @Query(
            """
            select c from Cliente c
            join c.usuario u
            where (:ativo is null or u.ativo = :ativo)
              and (
                  :busca is null
                  or lower(u.nome) like :busca escape '!'
                  or lower(u.email) like :busca escape '!'
                  or exists (
                      select e.id from Cliente vinculado
                      join vinculado.empresas e
                      where vinculado.id = c.id
                        and lower(e.legalName) like :busca escape '!'
                  )
              )
            order by u.nome, c.id
            """)
    List<Cliente> pesquisar(String busca, Boolean ativo);
}
