package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.notification.OutboundMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Leitura e gravacao das credenciais de uso unico de uma conta.
 *
 * O relogio e o do banco em toda comparacao de prazo, e nao o da aplicacao: duas
 * instancias nao podem discordar sobre quando um link venceu.
 */
interface AccountConfirmationRepository extends JpaRepository<AccountConfirmation, Long> {

    /**
     * O identificador da conta de um link que ainda vale, dentro da aplicacao de quem
     * chamou.
     *
     * A aplicacao entra no proprio WHERE, e nao numa comparacao depois. Medido antes disto,
     * a rota de confirmacao era o unico lugar do servico que nao isolava por aplicacao: a
     * aplicacao B confirmava conta da aplicacao A apresentando o link dela.
     *
     * As quatro condicoes vivem juntas de proposito. Link desconhecido, usado, vencido e de
     * proposito errado precisam responder igual, e separa-las em caminhos faria cada uma
     * ganhar resposta propria.
     */
    @Query("""
            select confirmation.clientAccountId
              from AccountConfirmation confirmation, ClientAccount account
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
     * Marca o link como usado e devolve quantas linhas isso afetou.
     *
     * A condicao de ainda nao ter sido usado vive no proprio comando, e o numero de linhas
     * afetadas vira a decisao: duas chamadas simultaneas com o mesmo link nao conseguem as
     * duas achar que ganharam.
     */
    @Modifying
    @Query("""
            update AccountConfirmation confirmation
               set confirmation.usedAt = offset datetime
             where confirmation.tokenDigest = :digest and confirmation.usedAt is null
            """)
    int consumeByDigest(@Param("digest") byte[] digest);

    /**
     * Derruba os links pendentes de um proposito so.
     *
     * A recuperacao usa isto ao emitir um link novo, porque a regra diz que o anterior morre
     * quando o novo nasce. Restringir ao proposito importa: um pedido de recuperacao nao pode
     * derrubar a confirmacao de cadastro que a pessoa ainda nao usou.
     */
    @Modifying
    @Query("""
            update AccountConfirmation confirmation
               set confirmation.usedAt = offset datetime
             where confirmation.clientAccountId = :accountId
               and confirmation.usedAt is null
               and confirmation.purpose = :purpose
            """)
    void invalidatePendingOf(@Param("accountId") long accountId,
            @Param("purpose") OutboundMessage.Purpose purpose);

    /**
     * Derruba todos os links pendentes da conta.
     *
     * Trocar a senha invalida os pendentes porque recuperacao existe exatamente para o caso
     * de a conta estar comprometida: deixar o link anterior vivo devolveria o acesso a quem
     * pediu primeiro.
     */
    @Modifying
    @Query("""
            update AccountConfirmation confirmation
               set confirmation.usedAt = offset datetime
             where confirmation.clientAccountId = :accountId and confirmation.usedAt is null
            """)
    void invalidateEveryPendingOf(@Param("accountId") long accountId);

    /**
     * Apaga os links de uma conta que esta sendo excluida, dentro da aplicacao dela.
     *
     * A chave estrangeira existe justamente para impedir que sobrem links apontando para o
     * nada, entao eles saem antes da conta.
     */
    @Modifying
    @Query("""
            delete from AccountConfirmation confirmation
             where confirmation.clientAccountId in (
                   select account.id from ClientAccount account
                    where account.id = :accountId and account.applicationId = :applicationId)
            """)
    void deleteWithinApplication(@Param("accountId") long accountId,
            @Param("applicationId") long applicationId);

    @Modifying
    @Query("delete from AccountConfirmation confirmation"
            + " where confirmation.clientAccountId = :accountId")
    void deleteAcrossApplications(@Param("accountId") long accountId);
}
