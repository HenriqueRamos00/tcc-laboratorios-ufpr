package br.ufpr.tcc.auth.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * O que a recusa de validacao escreve no log.
 *
 * Medido na aplicacao no ar: o resolvedor de excecao do proprio servidor de rotas registra a
 * falha de validacao inteira em nivel de aviso, e a mensagem dela carrega o **valor recusado**.
 * Senha de um caractere a mais que o teto, ou senha colada com sufixo, ia para o log do
 * contentor em texto claro, junto com o endereco de quem digitou e-mail fora do formato. Pela
 * rota de entrada administrativa, que e aberta, isso valia para qualquer um na rede.
 *
 * A regra de log do modulo lista senha e corpo de requisicao de login entre o que **nunca** se
 * registra, e esta classe e o que impede a regressao: ela captura tudo o que a aplicacao
 * escreve durante a requisicao e exige que o valor submetido nao esteja em lugar nenhum.
 */
@AutoConfigureMockMvc
class ValidationFailureDoesNotLeakIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(995_000L);
    private static final String SENTINEL_PASSWORD = "SentinelaDeSenhaQueNaoPodeChegarAoLog";
    private static final String SENTINEL_ADDRESS = "endereco-sentinela-sem-arroba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    private final ListAppender<ILoggingEvent> everything = new ListAppender<>();
    private Logger rootLogger;
    private String applicationToken;
    private String applicationId;

    @BeforeEach
    void captureEveryLogLine() {
        rootLogger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT");
        everything.start();
        rootLogger.addAppender(everything);
        rootLogger.setLevel(Level.DEBUG);

        long identifier = NEXT.incrementAndGet();
        applicationId = Long.toString(identifier);
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", identifier).param("code", "validacao-" + identifier)
                .param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT.incrementAndGet()).param("applicationId", identifier)
                .param("digest", token.digest().asBytes())
                .param("now", OffsetDateTime.now()).update();
        applicationToken = token.clearValue();
    }

    @AfterEach
    void releaseLogLines() {
        rootLogger.detachAppender(everything);
        everything.stop();
    }

    private void assertNothingLeaked() {
        assertThat(everything.list).isNotEmpty();
        assertThat(everything.list).allSatisfy(event -> {
            String line = event.getFormattedMessage() + event.getKeyValuePairs();
            assertThat(line).doesNotContain(SENTINEL_PASSWORD).doesNotContain(SENTINEL_ADDRESS);
        });
    }

    @Test
    @DisplayName("senha recusada pela validacao nao chega ao log")
    void passwordRefusedByValidationDoesNotReachTheLog() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"pessoa.validacao@lactec.exemplo",
                                 "password":"%s","role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(SENTINEL_PASSWORD.repeat(3), applicationId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertNothingLeaked();
    }

    @Test
    @DisplayName("endereco recusado pela validacao nao chega ao log")
    void addressRefusedByValidationDoesNotReachTheLog() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaOk123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(SENTINEL_ADDRESS, applicationId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertNothingLeaked();
    }

    /**
     * A entrada administrativa e rota aberta, sem credencial de aplicacao, entao o que ela
     * registra na recusa de validacao vale para qualquer um que alcance a porta.
     */
    @Test
    @DisplayName("a entrada administrativa recusada pela validacao nao chega ao log")
    void administrativeSignInRefusedByValidationDoesNotReachTheLog() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + SENTINEL_ADDRESS + "\",\"password\":\""
                                + SENTINEL_PASSWORD + "\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertNothingLeaked();
    }

    /** Corpo que o desserializador recusa entra pelo mesmo caminho e tambem nao pode vazar. */
    @Test
    @DisplayName("campo desconhecido no corpo nao leva o valor submetido ao log")
    void unknownFieldDoesNotCarryTheSubmittedValueToTheLog() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa\",\"cnpj\":\"55555555555555\","
                                + "\"apelido\":\"" + SENTINEL_ADDRESS + "\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertNothingLeaked();
    }
}
