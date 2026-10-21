package br.ufpr.tcc.backend_auth.domain.repository;

import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailAndServicoIdAndServicoAtivoTrueAndPerfilIsNotNull(String email, Long servicoId);
    List<Usuario> findAllByServicoIdAndServicoAtivoTrueAndPerfilIsNotNullOrderByNomeAscIdAsc(Long servicoId);
    Optional<Usuario> findByIdAndServicoIdAndServicoAtivoTrueAndPerfilIsNotNull(Long id, Long servicoId);
    boolean existsByServicoId(Long servicoId);
    boolean existsByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Usuario> findByEmailAndAdministradorServicoTrue(String email);
    boolean existsByAdministradorServicoTrue();
    boolean existsByEmailAndIdNot(String email, Long id);
    List<Usuario> findAllByPerfilIsNotNullOrderByNomeAscIdAsc();
    List<Usuario> findAllByAdministradorServicoTrueOrderByNomeAscIdAsc();
    Optional<Usuario> findByIdAndPerfilIsNotNull(Long id);
    Optional<Usuario> findByIdAndAdministradorServicoTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.administradorServico = true order by u.id")
    List<Usuario> bloquearAdministradores();
}
