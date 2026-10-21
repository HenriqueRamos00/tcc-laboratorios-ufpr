package br.ufpr.tcc.backend_auth.domain.repository;

import br.ufpr.tcc.backend_auth.domain.model.entity.TokenServico;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TokenServicoRepository extends JpaRepository<TokenServico, Long> {
    @EntityGraph(attributePaths = "servico")
    Optional<TokenServico> findByTokenHash(String tokenHash);
    List<TokenServico> findAllByServicoIdOrderByCriadoEmDescIdDesc(Long servicoId);
    Optional<TokenServico> findByIdAndServicoId(Long id, Long servicoId);

    @Modifying
    @Query("delete from TokenServico t where t.servico.id = :servicoId")
    void excluirPorServico(Long servicoId);
}
