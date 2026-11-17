package br.ufpr.tcc.auth.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class DemoSeedIntegrationTest extends OracleIntegrationTestBase {

    private static final String TOKEN = "svc_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String ADMIN_PASSWORD = "Lactec!7uQ2mN9xR4pK";
    private static final String CLIENT1_PASSWORD = "Cliente1!6vT9qL2nW8sD";
    private static final String CLIENT2_PASSWORD = "Cliente2!3rK7wP5mH9zF";

    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwords;
    @Autowired private TransactionTemplate transactions;
    @Autowired private DatabaseProbe database;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void populate() {
        seed();
    }

    private void seed() {
        DemoSeedRunner runner = new DemoSeedRunner(entityManager, passwords, TOKEN, 2592000,
                ADMIN_PASSWORD, CLIENT1_PASSWORD, CLIENT2_PASSWORD);
        transactions.executeWithoutResult(status -> runner.run(null));
    }

    @AfterEach
    void removeDemoRecords() {
        database.statement("DELETE FROM ADMINISTRATIVE_SESSION WHERE ADMINISTRATOR_ID IN"
                + " (SELECT ID FROM ADMINISTRATOR WHERE EMAIL = 'lactec@lactec.com')").run();
        database.statement("DELETE FROM ADMINISTRATOR WHERE EMAIL = 'lactec@lactec.com'").run();
        database.statement("DELETE FROM CLIENT_ACCOUNT WHERE APPLICATION_ID IN"
                + " (SELECT ID FROM APPLICATION WHERE CODE = 'portal-web')").run();
        database.statement("DELETE FROM COMPANY WHERE CNPJ IN ('30960572000183', '11222333000181')").run();
        database.statement("DELETE FROM APPLICATION_TOKEN WHERE APPLICATION_ID IN"
                + " (SELECT ID FROM APPLICATION WHERE CODE = 'portal-web')").run();
        database.statement("DELETE FROM APPLICATION WHERE CODE = 'portal-web'").run();
    }

    @Test
    void seededAccountsSignInWithThePortalContractAndTheirOwnCompany() throws Exception {
        String[][] clients = {
            {"client1@lactec.com", CLIENT1_PASSWORD, "30960572000183"},
            {"client2@lactec.com", CLIENT2_PASSWORD, "11222333000181"}
        };
        for (String[] client : clients) {
            mvc.perform(post("/api/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + client[0] + "\",\"password\":\"" + client[1] + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CLIENTE"))
                    .andExpect(jsonPath("$.serviceCode").value("portal-web"))
                    .andExpect(jsonPath("$.authorizations.companyCnpjs.length()").value(1))
                    .andExpect(jsonPath("$.authorizations.companyCnpjs[0]").value(client[2]))
                    .andExpect(jsonPath("$.companyCnpjs").doesNotExist());
        }
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lactec@lactec.com\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lactec@lactec.com\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatSeedPreservesEditedPasswordsSituationsAndCompanyLinks() {
        String changedHash = passwords.encode("NovaSenha!8mV4rT9q");
        database.statement("UPDATE CLIENT_ACCOUNT SET PASSWORD_HASH = :hash, ACTIVE = 0, EMAIL_CONFIRMED = 0"
                + " WHERE EMAIL = 'client1@lactec.com'").with("hash", changedHash).run();
        database.statement("DELETE FROM CLIENT_ACCOUNT_COMPANY WHERE CLIENT_ACCOUNT_ID IN"
                + " (SELECT ID FROM CLIENT_ACCOUNT WHERE EMAIL = 'client1@lactec.com')").run();
        database.statement("UPDATE ADMINISTRATOR SET ACTIVE = FALSE WHERE EMAIL = 'lactec@lactec.com'").run();
        database.statement("UPDATE APPLICATION SET ACTIVE = FALSE WHERE CODE = 'portal-web'").run();

        seed();

        assertThat(database.statement("SELECT COUNT(*) FROM CLIENT_ACCOUNT WHERE EMAIL = 'client1@lactec.com'"
                + " AND PASSWORD_HASH = :hash AND ACTIVE = 0 AND EMAIL_CONFIRMED = 0")
                .with("hash", changedHash).count()).isEqualTo(1);
        assertThat(database.statement("SELECT COUNT(*) FROM CLIENT_ACCOUNT_COMPANY WHERE CLIENT_ACCOUNT_ID IN"
                + " (SELECT ID FROM CLIENT_ACCOUNT WHERE EMAIL = 'client1@lactec.com')").count()).isZero();
        assertThat(database.statement("SELECT COUNT(*) FROM ADMINISTRATOR"
                + " WHERE EMAIL = 'lactec@lactec.com' AND ACTIVE = FALSE").count()).isEqualTo(1);
        assertThat(database.statement("SELECT COUNT(*) FROM APPLICATION WHERE CODE = 'portal-web'"
                + " AND ACTIVE = FALSE").count()).isEqualTo(1);
        assertThat(database.statement("SELECT COUNT(*) FROM CLIENT_ACCOUNT WHERE EMAIL IN"
                + " ('client1@lactec.com', 'client2@lactec.com')").count()).isEqualTo(2);
    }

    @Test
    void expiredIntegrationTokenIsRenewedButRevocationIsPreserved() {
        database.statement("UPDATE APPLICATION_TOKEN SET EXPIRES_AT = :past WHERE APPLICATION_ID IN"
                + " (SELECT ID FROM APPLICATION WHERE CODE = 'portal-web')")
                .with("past", OffsetDateTime.now().minusDays(1)).run();
        seed();
        assertThat(database.statement("SELECT COUNT(*) FROM APPLICATION_TOKEN WHERE APPLICATION_ID IN"
                + " (SELECT ID FROM APPLICATION WHERE CODE = 'portal-web') AND EXPIRES_AT > SYSTIMESTAMP")
                .count()).isEqualTo(1);

        database.statement("UPDATE APPLICATION_TOKEN SET EXPIRES_AT = :past, REVOKED_AT = :now"
                + " WHERE APPLICATION_ID IN (SELECT ID FROM APPLICATION WHERE CODE = 'portal-web')")
                .with("past", OffsetDateTime.now().minusDays(1)).with("now", OffsetDateTime.now()).run();
        seed();
        assertThat(database.statement("SELECT COUNT(*) FROM APPLICATION_TOKEN WHERE APPLICATION_ID IN"
                + " (SELECT ID FROM APPLICATION WHERE CODE = 'portal-web')"
                + " AND EXPIRES_AT < SYSTIMESTAMP AND REVOKED_AT IS NOT NULL").count()).isEqualTo(1);
    }
}
