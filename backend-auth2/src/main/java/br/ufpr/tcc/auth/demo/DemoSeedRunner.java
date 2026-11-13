package br.ufpr.tcc.auth.demo;

import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.credential.model.ServiceToken;
import io.hypersistence.tsid.TSID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Dados exclusivos do Compose local; nunca participa das migracoes do esquema. */
@Component
@ConditionalOnProperty(name = "auth.demo.seed.enabled", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    private final EntityManager database;
    private final PasswordEncoder passwords;
    private final TSID.Factory identifiers = TSID.Factory.builder().build();
    private final ServiceToken serviceToken;
    private final long expirationSeconds;
    private final String adminPassword;
    private final String client1Password;
    private final String client2Password;

    public DemoSeedRunner(EntityManager database, PasswordEncoder passwords,
            @Value("${auth.demo.seed.service-token}") String serviceToken,
            @Value("${auth.demo.seed.token-expiration-seconds}") long expirationSeconds,
            @Value("${auth.demo.seed.admin-password}") String adminPassword,
            @Value("${auth.demo.seed.client1-password}") String client1Password,
            @Value("${auth.demo.seed.client2-password}") String client2Password) {
        this.database = database;
        this.passwords = passwords;
        this.serviceToken = ServiceToken.fromPresentedValue(serviceToken);
        if (expirationSeconds < 1 || expirationSeconds > 31536000) {
            throw new IllegalArgumentException("Validade do token demo deve estar entre 1 e 31536000 segundos");
        }
        this.expirationSeconds = expirationSeconds;
        this.adminPassword = requirePassword(adminPassword, "AUTH_DEMO_ADMIN_PASSWORD");
        this.client1Password = requirePassword(client1Password, "AUTH_DEMO_CLIENT1_PASSWORD");
        this.client2Password = requirePassword(client2Password, "AUTH_DEMO_CLIENT2_PASSWORD");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long applicationId = identifier("SELECT ID FROM APPLICATION WHERE CODE = 'portal-web'")
                .orElseGet(() -> {
                    long id = nextId();
                    query("""
                            INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)
                            VALUES (:id, 'portal-web', 'Portal Web', TRUE, :now, :now)
                            """, "id", id, "now", OffsetDateTime.now()).executeUpdate();
                    return id;
                });
        long energy = company("EMPRESA DE ENERGIA", "30960572000183");
        long distributor = company("DISTRIBUIDORA DE ENERGIA", "11222333000181");
        administrator();
        client(applicationId, energy, "Cliente 1", "client1@lactec.com", client1Password);
        client(applicationId, distributor, "Cliente 2", "client2@lactec.com", client2Password);
        integrationToken(applicationId);
    }

    private long company(String name, String cnpj) {
        return identifier("SELECT ID FROM COMPANY WHERE CNPJ = :cnpj", "cnpj", cnpj)
                .orElseGet(() -> {
                    long id = nextId();
                    query("""
                            INSERT INTO COMPANY (ID, NAME, CNPJ, CREATED_AT, UPDATED_AT)
                            VALUES (:id, :name, :cnpj, :now, :now)
                            """, "id", id, "name", name, "cnpj", cnpj, "now", OffsetDateTime.now())
                            .executeUpdate();
                    return id;
                });
    }

    private void administrator() {
        String email = "lactec@lactec.com";
        if (identifier("SELECT ID FROM ADMINISTRATOR WHERE EMAIL_NORMALIZED = LOWER(:email)",
                "email", email).isPresent()) {
            return;
        }
        query("""
                INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, PASSWORD_HASH,
                                           ACTIVE, CREATED_AT, UPDATED_AT)
                VALUES (:id, 'Administrador Lactec', :email, LOWER(:email), :hash, TRUE, :now, :now)
                """, "id", nextId(), "email", email, "hash", passwords.encode(adminPassword),
                "now", OffsetDateTime.now()).executeUpdate();
    }

    private void client(long applicationId, long companyId, String name, String email, String password) {
        if (identifier("SELECT ID FROM CLIENT_ACCOUNT WHERE APPLICATION_ID = :application"
                        + " AND EMAIL_NORMALIZED = LOWER(:email)",
                "application", applicationId, "email", email).isPresent()) {
            return;
        }
        long id = nextId();
        query("""
                INSERT INTO CLIENT_ACCOUNT (ID, APPLICATION_ID, NAME, EMAIL, EMAIL_NORMALIZED,
                                            PASSWORD_HASH, EMAIL_CONFIRMED, ACTIVE, CREATED_AT, UPDATED_AT)
                VALUES (:id, :application, :name, :email, LOWER(:email), :hash, 1, 1, :now, :now)
                """, "id", id, "application", applicationId, "name", name, "email", email,
                "hash", passwords.encode(password), "now", OffsetDateTime.now()).executeUpdate();
        query("INSERT INTO CLIENT_ACCOUNT_COMPANY (CLIENT_ACCOUNT_ID, COMPANY_ID) VALUES (:id, :company)",
                "id", id, "company", companyId).executeUpdate();
    }

    private void integrationToken(long applicationId) {
        byte[] digest = serviceToken.digest().asBytes();
        Optional<Long> existing = identifier("SELECT APPLICATION_ID FROM APPLICATION_TOKEN"
                + " WHERE TOKEN_DIGEST = :digest", "digest", digest);
        if (existing.isPresent()) {
            if (existing.get() != applicationId) {
                throw new IllegalStateException("Token demo ja pertence a outra aplicacao");
            }
            // Nao desfaz revogacao manual. Apenas renova o vencido, como o seed anterior.
            query("""
                    UPDATE APPLICATION_TOKEN SET EXPIRES_AT = :expires
                     WHERE TOKEN_DIGEST = :digest AND REVOKED_AT IS NULL
                       AND EXPIRES_AT <= :now
                    """, "digest", digest, "expires", OffsetDateTime.now().plusSeconds(expirationSeconds),
                    "now", OffsetDateTime.now()).executeUpdate();
            return;
        }
        query("""
                INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST, CREATED_AT, EXPIRES_AT)
                VALUES (:id, :application, 'Portal Web - demonstracao local', :digest, :now, :expires)
                """, "id", nextId(), "application", applicationId, "digest", digest,
                "now", OffsetDateTime.now(), "expires", OffsetDateTime.now().plusSeconds(expirationSeconds))
                .executeUpdate();
    }

    private long nextId() {
        return identifiers.generate().toLong();
    }

    // O seed atravessa as funcionalidades, sem tornar as entidades internas publicas.
    // Os comandos nativos usam a mesma conexao JPA restrita e uma unica transacao.
    private Query query(String sql, Object... parameters) {
        Query query = database.createNativeQuery(sql);
        for (int index = 0; index < parameters.length; index += 2) {
            query.setParameter((String) parameters[index], parameters[index + 1]);
        }
        return query;
    }

    private Optional<Long> identifier(String sql, Object... parameters) {
        List<?> rows = query(sql, parameters).getResultList();
        return rows.stream()
                .map(value -> ((Number) value).longValue()).findFirst();
    }

    private static String requirePassword(String password, String variable) {
        if (password == null || password.length() > 72 || !PasswordPolicy.violationsOf(password).isEmpty()) {
            throw new IllegalArgumentException(variable
                    + " deve ter de 8 a 72 caracteres, com maiuscula, minuscula e numero");
        }
        return password;
    }
}
