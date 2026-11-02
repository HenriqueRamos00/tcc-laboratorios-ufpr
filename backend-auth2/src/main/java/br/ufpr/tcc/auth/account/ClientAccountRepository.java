package br.ufpr.tcc.auth.account;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Leitura e gravacao da tabela de conta de cliente.
 *
 * Nao existe aqui consulta que receba apenas o e-mail. A assinatura exige a aplicacao, e
 * com isso ler conta de outra aplicacao deixa de ser descuido possivel e passa a ser codigo
 * que nao compila.
 *
 * Quem passa o endereco para minuscula e o banco, com LOWER sobre o parametro, pelo mesmo
 * motivo que o transformador de coluna da entidade: calcular a minuscula em Java faria a
 * consulta discordar da restricao que o banco cobra.
 */
interface ClientAccountRepository extends JpaRepository<ClientAccount, Long> {

    @Query("""
            select count(account) from ClientAccount account
             where account.applicationId = :applicationId
               and account.emailNormalized = lower(:email)
            """)
    long countWithinApplicationByEmail(@Param("applicationId") long applicationId,
            @Param("email") String email);

    @Query("""
            select account from ClientAccount account
             where account.applicationId = :applicationId
               and account.emailNormalized = lower(:email)
            """)
    Optional<ClientAccount> findWithinApplicationByEmail(
            @Param("applicationId") long applicationId, @Param("email") String email);

    /**
     * A credencial guardada de um cliente, sempre dentro de uma aplicacao.
     *
     * A juncao com a empresa carrega a aplicacao na propria condicao, e nao apenas o
     * identificador: a chave estrangeira do banco e composta pelo mesmo motivo, porque com a
     * empresa referida por identificador sozinho nada impediria ler empresa da aplicacao B
     * para uma conta da aplicacao A.
     *
     * A juncao e externa porque o vinculo com empresa e opcional, e conta sem empresa precisa
     * ser encontrada do mesmo jeito.
     */
    @Query("""
            select new br.ufpr.tcc.auth.account.StoredCredentialRow(
                   account.id, account.name, account.email, account.passwordHash,
                   account.emailConfirmed, account.active, company.cnpj)
              from ClientAccount account
              left join Company company
                on company.applicationId = account.applicationId
               and company.id = account.companyId
             where account.applicationId = :applicationId
               and account.emailNormalized = lower(:email)
            """)
    Optional<StoredCredentialRow> findCredentialWithinApplication(
            @Param("applicationId") long applicationId, @Param("email") String email);

    List<ClientAccount> findByApplicationIdOrderByCreatedAtDesc(long applicationId);

    List<ClientAccount> findByOrderByCreatedAtDesc();

    Optional<ClientAccount> findByIdAndApplicationId(long id, long applicationId);

    /**
     * A alteracao leva a aplicacao de quem chamou no proprio comando, e nao apenas na
     * consulta que roda antes.
     *
     * Sem isso o isolamento entre aplicacoes viveria inteiro naquela consulta: trocar a
     * consulta por uma sem escopo passaria a permitir que a aplicacao A renomeasse e
     * desativasse conta de cliente da aplicacao B, e nenhum teste cairia. Desativar conta
     * alheia e negacao de servico contra o cliente de outra aplicacao do Lactec, entao a
     * condicao e repetida de proposito.
     */
    @Modifying
    @Query("""
            update ClientAccount account
               set account.name = :name, account.active = :active, account.updatedAt = :now
             where account.id = :id and account.applicationId = :applicationId
            """)
    void updateWithinApplication(@Param("id") long id,
            @Param("applicationId") long applicationId, @Param("name") String name,
            @Param("active") boolean active, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
            update ClientAccount account
               set account.name = :name, account.active = :active, account.updatedAt = :now
             where account.id = :id
            """)
    void updateAcrossApplications(@Param("id") long id, @Param("name") String name,
            @Param("active") boolean active, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("delete from ClientAccount account"
            + " where account.id = :id and account.applicationId = :applicationId")
    void deleteWithinApplication(@Param("id") long id,
            @Param("applicationId") long applicationId);

    @Modifying
    @Query("delete from ClientAccount account where account.id = :id")
    void deleteAcrossApplications(@Param("id") long id);
}
