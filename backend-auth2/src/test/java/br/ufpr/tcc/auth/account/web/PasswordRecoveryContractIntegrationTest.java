package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.notification.RecordedMessages;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prova a recuperacao de senha.
 *
 * O caso que sustenta a fatia e o do endereco inexistente: ele responde igual ao do
 * endereco cadastrado, no mesmo formato. Qualquer diferenca ali transforma a rota em
 * consulta de quem tem conta no Lactec, que e exatamente o que a matriz de codigos evita
 * no login.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.notification.record-messages=true")
class PasswordRecoveryContractIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT_ID = new AtomicLong(400_000L);
    private static final String FIRST_PASSWORD = "SenhaForte123";
    private static final String NEW_PASSWORD = "OutraSenha456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RecordedMessages recordedMessages;

    private String applicationToken;
    private String applicationId;

    @BeforeEach
    void registerApplication() {
        recordedMessages.clear();
        long id = NEXT_ID.incrementAndGet();
        String code = "recuperacao-" + id;
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", id).param("code", code).param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT_ID.incrementAndGet()).param("applicationId", id)
                .param("digest", token.digest().asBytes()).param("now", OffsetDateTime.now()).update();
        applicationToken = token.clearValue();
        applicationId = Long.toString(id);
    }

    private MvcResult send(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private String registerConfirmedAccount() throws Exception {
        String email = "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
        send("/api/users", """
                {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE","serviceId":"%s"}"""
                .formatted(email, FIRST_PASSWORD, applicationId));
        send("/api/auth/confirmations",
                "{\"token\":\"" + recordedMessages.lastTokenFor(email) + "\"}");
        return email;
    }

    private int loginStatus(String email, String password) throws Exception {
        return send("/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .getResponse().getStatus();
    }

    @Test
    @DisplayName("pedido de recuperacao responde igual para endereco que existe e que nao existe")
    void recoveryAnswersAlikeForKnownAndUnknownAddresses() throws Exception {
        String registered = registerConfirmedAccount();

        MvcResult known = send("/api/auth/recoveries", "{\"email\":\"" + registered + "\"}");
        MvcResult unknown = send("/api/auth/recoveries", "{\"email\":\"ninguem@exemplo.com\"}");

        assertThat(known.getResponse().getStatus()).isEqualTo(202);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(202);
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(known.getResponse().getContentAsString());
        assertThat(recordedMessages.lastTokenFor("ninguem@exemplo.com"))
                .as("endereco inexistente nao gera link").isNull();
    }

    @Test
    @DisplayName("com o link, a senha troca e a antiga para de valer")
    void passwordChangesWithTheLinkAndTheOldOneStopsWorking() throws Exception {
        String email = registerConfirmedAccount();
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String link = recordedMessages.lastTokenFor(email);

        MvcResult reset = send("/api/auth/password-resets",
                "{\"token\":\"" + link + "\",\"password\":\"" + NEW_PASSWORD + "\"}");

        assertThat(reset.getResponse().getStatus()).isEqualTo(204);
        assertThat(loginStatus(email, NEW_PASSWORD)).as("senha nova").isEqualTo(200);
        assertThat(loginStatus(email, FIRST_PASSWORD)).as("senha antiga").isEqualTo(401);
    }

    // Recuperacao existe para o caso de a conta estar comprometida: deixar os
    // links pendentes vivos devolveria o acesso a quem pediu o primeiro.
    @Test
    @DisplayName("trocar a senha derruba os links pendentes da conta")
    void changingThePasswordKillsThePendingLinks() throws Exception {
        String email = registerConfirmedAccount();
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String firstLink = recordedMessages.lastTokenFor(email);
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String secondLink = recordedMessages.lastTokenFor(email);

        send("/api/auth/password-resets",
                "{\"token\":\"" + secondLink + "\",\"password\":\"" + NEW_PASSWORD + "\"}");

        MvcResult withFirstLink = send("/api/auth/password-resets",
                "{\"token\":\"" + firstLink + "\",\"password\":\"MaisUmaSenha789\"}");
        assertThat(withFirstLink.getResponse().getStatus()).isEqualTo(400);
    }

    // O proposito faz parte do que e verificado: senao um link de confirmacao,
    // que chega a quem acabou de se cadastrar, trocaria senha.
    @Test
    @DisplayName("link de confirmacao nao troca senha")
    void confirmationLinkCannotResetThePassword() throws Exception {
        String email = "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
        send("/api/users", """
                {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE","serviceId":"%s"}"""
                .formatted(email, FIRST_PASSWORD, applicationId));
        String confirmationLink = recordedMessages.lastTokenFor(email);

        MvcResult result = send("/api/auth/password-resets",
                "{\"token\":\"" + confirmationLink + "\",\"password\":\"" + NEW_PASSWORD + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("o link de recuperacao serve uma vez so")
    void recoveryLinkWorksOnlyOnce() throws Exception {
        String email = registerConfirmedAccount();
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String link = recordedMessages.lastTokenFor(email);
        send("/api/auth/password-resets",
                "{\"token\":\"" + link + "\",\"password\":\"" + NEW_PASSWORD + "\"}");

        MvcResult again = send("/api/auth/password-resets",
                "{\"token\":\"" + link + "\",\"password\":\"MaisUmaSenha789\"}");

        assertThat(again.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("senha nova fora da politica e recusada")
    void newPasswordOutsideThePolicyIsRejected() throws Exception {
        String email = registerConfirmedAccount();
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String link = recordedMessages.lastTokenFor(email);

        MvcResult result = send("/api/auth/password-resets",
                "{\"token\":\"" + link + "\",\"password\":\"fraca\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(loginStatus(email, FIRST_PASSWORD)).as("a senha antiga continua valendo")
                .isEqualTo(200);
    }
}
