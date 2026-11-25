package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.AlteracaoEmail;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlteracaoEmailRepository extends JpaRepository<AlteracaoEmail, Long> {
    @Query("select a.usuarioId from AlteracaoEmail a where a.tokenAtualHash = :hash or a.tokenNovoHash = :hash")
    Optional<Long> encontrarUsuarioPorToken(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlteracaoEmail a where a.usuarioId = :usuarioId")
    Optional<AlteracaoEmail> encontrarPorUsuarioParaAtualizacao(@Param("usuarioId") Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlteracaoEmail a where a.tokenAtualHash = :hash or a.tokenNovoHash = :hash")
    Optional<AlteracaoEmail> encontrarPorTokenParaAtualizacao(@Param("hash") String hash);
}
