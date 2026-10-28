package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
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

    /**
     * Pedido novo mata o anterior.
     *
     * A regra escrita e "usado, expirado ou substituido por um novo, o anterior morre", e ela
     * existe porque recuperacao de senha e o fluxo de quem perdeu o acesso: link antigo que
     * continua valendo transforma caixa postal e historico de navegador em chave da conta.
     * Medido antes da correcao: dez pedidos deixavam dez links pendentes, os dez validos.
     */
    @Test
    @DisplayName("pedido novo de recuperacao invalida o link anterior")
    void newRecoveryRequestKillsThePreviousLink() throws Exception {
        String email = registerConfirmedAccount();
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String firstLink = recordedMessages.lastTokenFor(email);
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String secondLink = recordedMessages.lastTokenFor(email);
        assertThat(secondLink).isNotEqualTo(firstLink);

        MvcResult withTheFirst = send("/api/auth/password-resets",
                "{\"token\":\"" + firstLink + "\",\"password\":\"" + NEW_PASSWORD + "\"}");

        assertThat(withTheFirst.getResponse().getStatus())
                .as("o link anterior morreu quando o novo nasceu").isEqualTo(400);
        assertThat(pendingRecoveryLinksOf(email))
                .as("so o ultimo pedido fica pendente").isEqualTo(1);
    }

    /**
     * O prazo da recuperacao e proprio, e mais curto que o da confirmacao de cadastro.
     *
     * Os dois fluxos compartilhavam uma propriedade de 24 horas. Confirmacao de cadastro pode
     * esperar o dia inteiro, porque quem nao confirma nao entra; recuperacao e o contrario,
     * ela abre a conta, e a regra pede prazo curto.
     */
    @Test
    @DisplayName("o link de recuperacao vence antes do link de confirmacao de cadastro")
    void recoveryLinkExpiresSoonerThanTheConfirmationLink() throws Exception {
        String email = registerConfirmedAccount();

        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");

        assertThat(linkLifetimeInMinutesOf(email, "PASSWORD_RECOVERY"))
                .as("recuperacao tem prazo proprio e curto")
                .isLessThan(linkLifetimeInMinutesOf(email, "EMAIL_CONFIRMATION"));
    }

    /**
     * O link vai para o endereco cadastrado, nao para a grafia que o pedido trouxe.
     *
     * A busca da conta casa pela forma minuscula que o banco calcula, entao grafias
     * diferentes encontram a mesma conta. Mandar a mensagem para a grafia submetida faz o
     * link do titular sair para um endereco que nao e o cadastrado.
     */
    @Test
    @DisplayName("o link de recuperacao sai para o endereco cadastrado, em qualquer grafia pedida")
    void recoveryLinkGoesToTheRegisteredAddress() throws Exception {
        String registered = registerConfirmedAccount();

        send("/api/auth/recoveries",
                "{\"email\":\"" + registered.toUpperCase(java.util.Locale.ROOT) + "\"}");

        assertThat(recordedMessages.lastTokenFor(registered))
                .as("a mensagem saiu para o endereco cadastrado").isNotNull();
        assertThat(recordedMessages.lastTokenFor(registered.toUpperCase(java.util.Locale.ROOT)))
                .as("e nao para a grafia que o pedido trouxe").isNull();
    }

    /**
     * O link pertence a conta, e a conta pertence a uma aplicacao.
     *
     * Todo o resto do servico isola por aplicacao por construcao: a consulta de conta casa
     * aplicacao mais endereco, entao conta de outra aplicacao simplesmente nao e encontrada.
     * A rota de confirmacao fugia dessa regra, porque consultava o link so por digest e
     * proposito. Com isso a aplicacao B confirmava conta da aplicacao A, que e o unico lugar
     * onde a regra 5 tinha buraco.
     */
    @Test
    @DisplayName("link de uma aplicacao nao e consumido por outra")
    void linkOfOneApplicationIsNotConsumedByAnother() throws Exception {
        String email = "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
        send("/api/users", """
                {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE","serviceId":"%s"}"""
                .formatted(email, FIRST_PASSWORD, applicationId));
        String linkOfThisApplication = recordedMessages.lastTokenFor(email);
        String otherApplicationToken = registerAnotherApplication();

        MvcResult withTheOtherApplication = mockMvc.perform(post("/api/auth/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherApplicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + linkOfThisApplication + "\"}"))
                .andReturn();

        assertThat(withTheOtherApplication.getResponse().getStatus())
                .as("a outra aplicacao nao confirma conta que nao e dela").isEqualTo(400);
        assertThat(loginStatus(email, FIRST_PASSWORD))
                .as("a conta continua sem poder entrar").isEqualTo(401);
        assertThat(send("/api/auth/confirmations",
                "{\"token\":\"" + linkOfThisApplication + "\"}")
                .getResponse().getStatus())
                .as("e a aplicacao dona ainda consegue usar o link").isEqualTo(204);
    }

    private String registerAnotherApplication() {
        long id = NEXT_ID.incrementAndGet();
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", id).param("code", "outra-recuperacao-" + id)
                .param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT_ID.incrementAndGet()).param("applicationId", id)
                .param("digest", token.digest().asBytes())
                .param("now", OffsetDateTime.now()).update();
        return token.clearValue();
    }

    private int pendingRecoveryLinksOf(String email) {
        return jdbcClient.sql("SELECT COUNT(*) FROM ACCOUNT_CONFIRMATION confirmation"
                        + " JOIN CLIENT_ACCOUNT account ON account.ID"
                        + " = confirmation.CLIENT_ACCOUNT_ID"
                        + " WHERE account.EMAIL_NORMALIZED = LOWER(:email)"
                        + " AND confirmation.PURPOSE = 'PASSWORD_RECOVERY'"
                        + " AND confirmation.USED_AT IS NULL")
                .param("email", email).query(Integer.class).single();
    }

    private long linkLifetimeInMinutesOf(String email, String purpose) {
        return jdbcClient.sql("SELECT ROUND((CAST(confirmation.EXPIRES_AT AS DATE)"
                        + " - CAST(confirmation.CREATED_AT AS DATE)) * 1440)"
                        + " FROM ACCOUNT_CONFIRMATION confirmation"
                        + " JOIN CLIENT_ACCOUNT account ON account.ID"
                        + " = confirmation.CLIENT_ACCOUNT_ID"
                        + " WHERE account.EMAIL_NORMALIZED = LOWER(:email)"
                        + " AND confirmation.PURPOSE = :purpose"
                        + " FETCH FIRST 1 ROWS ONLY")
                .param("email", email).param("purpose", purpose).query(Long.class).single();
    }

    /**
     * O prazo do link precisa ser cobrado de verdade, e nao apenas gravado.
     *
     * Os casos de prazo que existiam comparavam os dois tempos **gravados** e afirmavam que o
     * da recuperacao e menor. Isso fixa a configuracao, nao o comportamento: apagar a
     * condicao de vencimento da consulta que consome o link deixava tudo verde, e um link de
     * uma hora parado numa caixa postal passava a valer como chave da conta para sempre. Dos
     * quatro motivos que a matriz lista para o mesmo 400 em link invalido, usado,
     * desconhecido, de proposito errado e vencido, o vencido era o unico sem caso.
     */
    @Test
    @DisplayName("link de recuperacao vencido responde igual ao desconhecido")
    void expiredRecoveryLinkAnswersLikeAnUnknownOne() throws Exception {
        String email = registerConfirmedAccount();
        send("/api/auth/recoveries", "{\"email\":\"" + email + "\"}");
        String link = recordedMessages.lastTokenFor(email);
        pushExpirationToThePast(email, "PASSWORD_RECOVERY");

        MvcResult expired = send("/api/auth/password-resets",
                "{\"token\":\"" + link + "\",\"password\":\"" + NEW_PASSWORD + "\"}");
        MvcResult unknown = send("/api/auth/password-resets",
                "{\"token\":\"" + ConfirmationToken.generate().clearValue()
                        + "\",\"password\":\"" + NEW_PASSWORD + "\"}");

        assertThat(expired.getResponse().getStatus()).isEqualTo(400);
        assertThat(expired.getResponse().getContentAsString())
                .as("vencido e desconhecido sao a mesma resposta")
                .isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(loginStatus(email, NEW_PASSWORD))
                .as("e a senha continua a antiga").isEqualTo(401);
        assertThat(loginStatus(email, FIRST_PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("link de confirmacao vencido responde igual ao desconhecido")
    void expiredConfirmationLinkAnswersLikeAnUnknownOne() throws Exception {
        String email = "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
        send("/api/users", """
                {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE","serviceId":"%s"}"""
                .formatted(email, FIRST_PASSWORD, applicationId));
        String link = recordedMessages.lastTokenFor(email);
        pushExpirationToThePast(email, "EMAIL_CONFIRMATION");

        MvcResult expired = send("/api/auth/confirmations", "{\"token\":\"" + link + "\"}");
        MvcResult unknown = send("/api/auth/confirmations",
                "{\"token\":\"" + ConfirmationToken.generate().clearValue() + "\"}");

        assertThat(expired.getResponse().getStatus()).isEqualTo(400);
        assertThat(expired.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(loginStatus(email, FIRST_PASSWORD))
                .as("a conta continua sem confirmacao, entao nao entra").isEqualTo(401);
    }

    /**
     * Adianta o relogio pelo unico lado possivel, recuando a coluna, porque esperar o prazo
     * real seria uma hora de suite. O prazo quem cobra e o banco, com SYSTIMESTAMP, entao
     * recuar a coluna exercita exatamente a condicao da consulta de consumo.
     */
    private void pushExpirationToThePast(String email, String purpose) {
        jdbcClient.sql("UPDATE ACCOUNT_CONFIRMATION SET EXPIRES_AT = SYSTIMESTAMP"
                        + " - INTERVAL '1' MINUTE"
                        + " WHERE USED_AT IS NULL AND PURPOSE = :purpose"
                        + " AND CLIENT_ACCOUNT_ID IN (SELECT ID FROM CLIENT_ACCOUNT"
                        + " WHERE EMAIL_NORMALIZED = LOWER(:email))")
                .param("email", email).param("purpose", purpose).update();
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
