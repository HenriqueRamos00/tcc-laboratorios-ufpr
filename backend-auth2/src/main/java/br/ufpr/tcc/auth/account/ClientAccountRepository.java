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
     * A credencial guardada de um cliente, sempre dentro de uma aplicacao, com uma linha
     * por empresa que a conta representa.
     *
     * As juncoes sao externas porque o vinculo com empresa e opcional: conta sem empresa
     * volta numa linha so, com o CNPJ nulo, e precisa ser encontrada do mesmo jeito.
     *
     * A lista de empresas vem na mesma consulta, e nao numa segunda, porque este e o
     * caminho mais quente do servico. Quem junta as linhas numa credencial so e o diretorio.
     */
    @Query("""
            select new br.ufpr.tcc.auth.account.StoredCredentialRow(
                   account.id, account.name, account.email, account.passwordHash,
                   account.emailConfirmed, account.active, company.cnpj)
              from ClientAccount account
              left join ClientAccountCompany link
                on link.accountId = account.id
              left join Company company
                on company.id = link.companyId
             where account.applicationId = :applicationId
               and account.emailNormalized = lower(:email)
            """)
    List<StoredCredentialRow> findCredentialWithinApplication(
            @Param("applicationId") long applicationId, @Param("email") String email);

    /**
     * A situacao da aplicacao em que uma conta vai nascer: vazio quando ela nao existe.
     *
     * Quem cadastra agora e o administrador, que informa a aplicacao no pedido em vez de
     * traze-la na credencial, entao a existencia e a situacao deixam de ser garantidas pela
     * cadeia de seguranca e passam a ser conferidas aqui.
     */
    @Query("select application.active from RegisteredApplication application"
            + " where application.id = :applicationId")
    Optional<Boolean> findApplicationSituation(@Param("applicationId") long applicationId);

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
