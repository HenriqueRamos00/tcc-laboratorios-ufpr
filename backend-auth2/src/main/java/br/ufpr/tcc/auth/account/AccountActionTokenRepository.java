package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.notification.OutboundMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas e comandos de tokens de uso unico. A verificacao de vencimento usa o relogio do
 * banco.
 */
interface AccountActionTokenRepository extends JpaRepository<AccountActionToken, Long> {

    /** Busca token nao usado e nao vencido, com finalidade e aplicacao correspondentes. */
    @Query("""
            select confirmation.clientAccountId
              from AccountActionToken confirmation, ClientAccount account
             where account.id = confirmation.clientAccountId
               and confirmation.tokenDigest = :digest
               and confirmation.purpose = :purpose
               and confirmation.usedAt is null
               and confirmation.expiresAt > offset datetime
               and account.applicationId = :applicationId
            """)
    Optional<Long> findLiveAccountFor(@Param("applicationId") long applicationId,
            @Param("digest") byte[] digest,
            @Param("purpose") OutboundMessage.Purpose purpose);

    /**
     * Marca o token como usado se ainda estiver pendente. Uma linha alterada significa consumo
     * bem-sucedido.
     */
    @Modifying
    @Query("""
            update AccountActionToken confirmation
               set confirmation.usedAt = offset datetime
             where confirmation.tokenDigest = :digest and confirmation.usedAt is null
            """)
    int consumeByDigest(@Param("digest") byte[] digest);

    /** Invalida pendentes apenas da finalidade informada, preservando os demais fluxos. */
    @Modifying
    @Query("""
            update AccountActionToken confirmation
               set confirmation.usedAt = offset datetime
             where confirmation.clientAccountId = :accountId
               and confirmation.usedAt is null
               and confirmation.purpose = :purpose
            """)
    void invalidatePendingOf(@Param("accountId") long accountId,
            @Param("purpose") OutboundMessage.Purpose purpose);

    /** Exclui os tokens antes da conta, respeitando a chave estrangeira e o escopo. */
    @Modifying
    @Query("""
            delete from AccountActionToken confirmation
             where confirmation.clientAccountId in (
                   select account.id from ClientAccount account
                    where account.id = :accountId and account.applicationId = :applicationId)
            """)
    void deleteWithinApplication(@Param("accountId") long accountId,
            @Param("applicationId") long applicationId);

    @Modifying
    @Query("delete from AccountActionToken confirmation"
            + " where confirmation.clientAccountId = :accountId")
    void deleteAcrossApplications(@Param("accountId") long accountId);
}
