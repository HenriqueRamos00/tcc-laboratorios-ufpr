package br.ufpr.tcc.auth.administrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova o caminho que a documentacao de integracao descreve, do primeiro administrador ate
 * uma aplicacao registrada com token emitido.
 *
 * Os casos sao ordenados de proposito: este e um fluxo, e a ordem faz parte do que esta
 * sendo provado. Em especial, a rota de instalacao precisa deixar de responder depois do
 * primeiro administrador, e so a ordem demonstra isso.
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "auth.setup.secret=segredo-de-instalacao-do-teste")
class BootstrapAndRegistrationContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static String administrativeToken;
    private static String applicationId;

    private final ListAppender<ILoggingEvent> auditLines = new ListAppender<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    /**
     * Esta classe e a unica que exercita a rota de instalacao, e a rota so responde
     * enquanto nao existir administrador nenhum. A precondicao e dela, entao e ela que a
     * estabelece: outras classes semeiam administrador para os proprios casos, e depender
     * da ordem entre classes seria defeito silencioso ate o dia em que a ordem muda.
     */
    @org.junit.jupiter.api.BeforeAll
    void captureAuditTrail() {
        ch.qos.logback.classic.Logger logger =
                ((LoggerContext) LoggerFactory.getILoggerFactory())
                        .getLogger(br.ufpr.tcc.auth.shared.observability.AuditTrail.class);
        auditLines.start();
        logger.addAppender(auditLines);
        logger.setLevel(Level.INFO);
    }

    private java.util.List<String> auditedEvents() {
        return auditLines.list.stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .filter(pair -> "event".equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .toList();
    }

    @org.junit.jupiter.api.BeforeAll
    void clearAdministrators() {
        jdbcClient.sql("DELETE FROM ADMINISTRATIVE_SESSION").update();
        jdbcClient.sql("DELETE FROM ADMINISTRATOR").update();
    }

    @Test
    @Order(1)
    @DisplayName("sem o segredo de instalacao, a rota de instalacao nao existe")
    void setupRouteDoesNotExistWithoutTheBootstrapSecret() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/setup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invasor","email":"invasor@exemplo.com",
                                 "password":"SenhaForte123"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @Order(2)
    @DisplayName("com o segredo, o primeiro administrador e criado")
    void firstAdministratorIsCreatedWithTheBootstrapSecret() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/setup/admin")
                        .header("X-Setup-Secret", "segredo-de-instalacao-do-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Administrador","email":"admin@lactec.exemplo",
                                 "password":"SenhaForte123"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(auditedEvents())
                .as("a conta mais privilegiada do servico nao pode nascer sem linha na trilha")
                .contains("administrator_installed");
    }

    // Fechar so por estado deixaria uma janela entre a subida do contentor e a
    // primeira chamada do operador, e quem chegasse primeiro viraria administrador.
    @Test
    @Order(3)
    @DisplayName("depois do primeiro, a rota de instalacao responde como inexistente")
    void setupRouteStopsExistingAfterTheFirstAdministrator() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/setup/admin")
                        .header("X-Setup-Secret", "segredo-de-instalacao-do-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo","email":"segundo@lactec.exemplo",
                                 "password":"SenhaForte123"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @Order(4)
    @DisplayName("senha errada no login administrativo responde 401 sem desafio")
    void administrativeLoginWithWrongPasswordAnswersWithoutChallenge() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@lactec.exemplo","password":"Errada123"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    @Order(5)
    @DisplayName("o administrador entra e recebe um token administrativo")
    void administratorSignsInAndReceivesAToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@lactec.exemplo","password":"SenhaForte123"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        administrativeToken = body.get("accessToken").asString();
        assertThat(administrativeToken).startsWith("adm_");
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
    }

    @Test
    @Order(6)
    @DisplayName("sem credencial administrativa, cadastrar aplicacao responde 401 com desafio")
    void registeringAnApplicationWithoutCredentialAnswersWithChallenge() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Portal Web","code":"portal-integracao"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    @Order(7)
    @DisplayName("o administrador cadastra uma aplicacao")
    void administratorRegistersAnApplication() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Portal Web","code":"portal-integracao"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        applicationId = body.get("id").asString();
        assertThat(applicationId).matches("[0-9]+");
        assertThat(body.get("code").asString()).isEqualTo("portal-integracao");
        assertThat(body.get("active").asBoolean()).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("codigo de aplicacao fora do formato do consumidor e recusado")
    void applicationCodeOutsideTheConsumerFormatIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","code":"Portal--Web"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @Order(9)
    @DisplayName("o administrador emite um token para a aplicacao, e ele aparece uma vez so")
    void administratorIssuesAnApplicationToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services/" + applicationId + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"token do portal"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        String issued = body.get("accessToken").asString();
        assertThat(issued).matches("^svc_[A-Za-z0-9_-]{43}$");
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
    }

    @Test
    @Order(10)
    @DisplayName("token de aplicacao nao serve para administrar")
    void applicationTokenDoesNotAdminister() throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/services/" + applicationId + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"outro token"}"""))
                .andReturn();
        String applicationToken =
                JSON.readTree(issued.getResponse().getContentAsString()).get("accessToken").asString();

        MvcResult result = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Escalada","code":"escalada"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }
}
