package br.ufpr.tcc.auth.account;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas e comandos de contas. Buscas por e-mail exigem a aplicacao e usam LOWER do banco. */
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
     * Busca a credencial e os CNPJs em uma consulta, com uma linha por empresa. LEFT JOIN preserva
     * contas sem empresas; o diretorio agrupa as linhas.
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

    /** Consulta a situacao da aplicacao informada no cadastro; vazio significa inexistente. */
    @Query("select application.active from RegisteredApplication application"
            + " where application.id = :applicationId")
    Optional<Boolean> findApplicationSituation(@Param("applicationId") long applicationId);

    List<ClientAccount> findByApplicationIdOrderByCreatedAtDesc(long applicationId);

    List<ClientAccount> findByOrderByCreatedAtDesc();

    Optional<ClientAccount> findByIdAndApplicationId(long id, long applicationId);

    /** Inclui a aplicacao no UPDATE para garantir o isolamento tambem na gravacao. */
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
