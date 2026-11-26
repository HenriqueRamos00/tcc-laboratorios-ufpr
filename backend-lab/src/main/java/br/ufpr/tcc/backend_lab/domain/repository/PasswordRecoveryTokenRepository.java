package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.PasswordRecoveryToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordRecoveryTokenRepository
        extends JpaRepository<PasswordRecoveryToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordRecoveryToken token where token.tokenDigest = :digest")
    Optional<PasswordRecoveryToken> findForUpdate(@Param("digest") String digest);

    @Modifying
    @Query("""
            update PasswordRecoveryToken token
            set token.consumedAt = :now
            where token.user.id = :userId and token.consumedAt is null
            """)
    int consumePendingForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
