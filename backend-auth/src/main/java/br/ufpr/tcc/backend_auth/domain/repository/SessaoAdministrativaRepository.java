package br.ufpr.tcc.backend_auth.domain.repository;

import br.ufpr.tcc.backend_auth.domain.model.entity.SessaoAdministrativa;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessaoAdministrativaRepository extends JpaRepository<SessaoAdministrativa, String> {
    @Override
    @EntityGraph(attributePaths = "usuario")
    Optional<SessaoAdministrativa> findById(String tokenHash);

    @Modifying
    @Query("delete from SessaoAdministrativa s where s.expiraEm <= :now")
    void excluirExpiradas(@Param("now") Instant now);

    @Modifying
    @Query("delete from SessaoAdministrativa s where s.usuario.id = :usuarioId")
    void excluirPorUsuario(@Param("usuarioId") Long usuarioId);
}
