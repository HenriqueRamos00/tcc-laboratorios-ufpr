package br.ufpr.tcc.auth.notification;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A aplicacao inteira com o servidor de e-mail fora do ar, contra servidor de verdade.
 *
 * O endereco aponta para o laco local numa porta que ninguem escuta, entao todo envio falha
 * pela pilha completa: caso de uso, evento de depois do commit, pool, disjuntor e adaptador. E
 * e isto que a regra de confirmacao por e-mail exige em uma frase: enviar e dependencia
 * externa, e ela nao pode segurar a resposta nem derrubar o cadastro.
 *
 * A copia em memoria das mensagens fica desligada aqui, e nao por descuido: com servidor
 * configurado ela recusa a subida, de proposito. Por isso a prova e no dado gravado e no
 * login, e nao em ler o token de volta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1",
        "auth.notification.sender=nao-responda@lactec.exemplo",
        "auth.notification.confirmation-link=https://portal.exemplo/confirmar?token={token}",
        "auth.notification.recovery-link=https://portal.exemplo/nova-senha?token={token}"
})
class MailDeliveryFailureIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(960_000L);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    private RestClient client;
    private String applicationToken;
    private String applicationId;

    @BeforeEach
    void registerApplication() {
        client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        long identifier = NEXT.incrementAndGet();
        applicationId = Long.toString(identifier);
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", identifier).param("code", "envio-" + identifier)
                .param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT.incrementAndGet()).param("applicationId", identifier)
                .param("digest", token.digest().asBytes())
                .param("now", OffsetDateTime.now()).update();
        applicationToken = token.clearValue();
    }

    /**
     * O caso obrigatorio: servidor de e-mail fora do ar nao derruba o cadastro.
     *
     * A conta nasce, o link pendente fica gravado, o login recusa por falta de confirmacao, e a
     * tentativa de envio aparece na contagem da sonda. Se o envio estivesse dentro da
     * transacao, o cadastro responderia erro de servidor e nao sobraria conta nenhuma.
     */
    @Test
    @DisplayName("servidor de e-mail fora do ar nao derruba o cadastro")
    void mailServerThatIsDownDoesNotBreakTheRegistration() throws Exception {
        String email = "cadastro." + NEXT.incrementAndGet() + "@lactec.exemplo";

        int registration = client.post().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                         "role":"CLIENTE","serviceId":"%s"}""".formatted(email, applicationId))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(registration).as("o cadastro nao depende do servidor de e-mail").isEqualTo(201);
        assertThat(accountsWith(email)).as("a conta ficou gravada").isEqualTo(1L);
        assertThat(pendingConfirmationsFor(email)).as("o link pendente ficou gravado")
                .isEqualTo(1L);

        int signIn = client.post().uri("/api/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"SenhaForte123\"}")
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(signIn).as("conta sem confirmacao nao entra").isEqualTo(401);

        assertThat(awaitAttemptedDeliveries()).as("a tentativa de envio foi contada").isPositive();
    }

    /**
     * A sonda do envio publica estado sem dado de pessoa, e a raiz da saude continua
     * respondendo. Funcao degradada nao e processo morto: 503 na raiz tiraria de rotacao o unico
     * servico de autenticacao que existe por causa de um e-mail que nao saiu.
     */
    @Test
    @DisplayName("a sonda do envio publica o estado sem endereco nem token, e a raiz responde")
    void theDeliveryProbePublishesStateWithoutPersonalData() {
        String body = client.get().uri("/health/mail").retrieve().body(String.class);

        assertThat(body).contains("\"transport\"").contains("\"state\"")
                .doesNotContain("@lactec.exemplo").doesNotContain("cnf_")
                .doesNotContain("127.0.0.1");
        int rootHealth = client.get().uri("/health")
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(rootHealth).as("a raiz da saude nao cai por causa do envio").isEqualTo(200);
    }

    private long accountsWith(String email) {
        return jdbcClient.sql("SELECT COUNT(*) FROM CLIENT_ACCOUNT"
                        + " WHERE EMAIL_NORMALIZED = LOWER(:email)")
                .param("email", email).query(Long.class).single();
    }

    private long pendingConfirmationsFor(String email) {
        return jdbcClient.sql("SELECT COUNT(*) FROM ACCOUNT_CONFIRMATION confirmation"
                        + " JOIN CLIENT_ACCOUNT account ON account.ID"
                        + " = confirmation.CLIENT_ACCOUNT_ID"
                        + " WHERE account.EMAIL_NORMALIZED = LOWER(:email)")
                .param("email", email).query(Long.class).single();
    }

    /**
     * O envio sai noutra thread, entao a contagem e esperada e nao lida de uma vez. O teto da
     * espera e curto porque recusa de conexao e imediata: ela nao gasta o tempo limite.
     */
    private long awaitAttemptedDeliveries() throws Exception {
        Duration limit = Duration.ofSeconds(10);
        long deadline = System.nanoTime() + limit.toNanos();
        long attempted = 0L;
        while (System.nanoTime() < deadline) {
            JsonNode details = JSON.readTree(client.get().uri("/health/mail")
                    .retrieve().body(String.class)).get("components").get("mailDelivery")
                    .get("details");
            attempted = details.get("failed").asLong() + details.get("delivered").asLong();
            if (attempted > 0L) {
                return attempted;
            }
            Thread.sleep(200L);
        }
        return attempted;
    }
}
