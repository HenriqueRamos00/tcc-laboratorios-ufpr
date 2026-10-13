package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {
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
