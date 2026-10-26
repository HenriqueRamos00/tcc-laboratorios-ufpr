package br.ufpr.tcc.auth.application.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova o ciclo de vida do token de aplicacao: emitir, listar e revogar.
 *
 * O caso que mais importa e o da revogacao: ela precisa cortar o acesso no mesmo instante,
 * porque token vazado que continua valendo ate vencer nao e revogavel, e o contrato promete
 * que e.
 *
 * O administrador desta classe e semeado direto na tabela, e nao pela rota de instalacao,
 * porque aquela rota responde uma vez so por banco: usa-la aqui criaria dependencia de
 * ordem entre classes de teste, que e defeito silencioso ate o dia em que a ordem muda.
 */
@AutoConfigureMockMvc
class ApplicationTokenLifecycleContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(300_000L);
    private static final String ADMINISTRATOR_PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String administrativeToken;
    private String applicationId;

    @BeforeEach
    void seedAdministratorAndApplication() throws Exception {
        String email = "admin.token" + NEXT.incrementAndGet() + "@lactec.exemplo";
        jdbcClient.sql("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador', :email, :email, TRUE, :hash, :now, :now)")
                .param("id", NEXT.incrementAndGet()).param("email", email)
                .param("hash", passwordEncoder.encode(ADMINISTRATOR_PASSWORD))
                .param("now", OffsetDateTime.now()).update();

        MvcResult signIn = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + ADMINISTRATOR_PASSWORD + "\"}"))
                .andReturn();
        administrativeToken = JSON.readTree(signIn.getResponse().getContentAsString())
                .get("accessToken").asString();

        applicationId = registerApplication("ciclo-token-" + NEXT.incrementAndGet());
    }

    private String registerApplication(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ciclo\",\"code\":\"" + code + "\"}"))
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    private String issueToken(String name) throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/services/" + applicationId + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return JSON.readTree(issued.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private JsonNode listTokens() throws Exception {
        MvcResult listing = mockMvc.perform(get("/api/services/" + applicationId + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();
        return JSON.readTree(listing.getResponse().getContentAsString());
    }

    private MvcResult loginWith(String applicationToken) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@exemplo.com","password":"Senha123"}"""))
                .andReturn();
    }

    @Test
    @DisplayName("a listagem mostra o token sem mostrar o segredo")
    void listingShowsTheTokenWithoutTheSecret() throws Exception {
        String secret = issueToken("token listado");

        JsonNode listing = listTokens();

        assertThat(listing.get(0).get("name").asString()).isEqualTo("token listado");
        assertThat(listing.toString()).doesNotContain(secret);
        assertThat(listing.get(0).has("accessToken")).as("a listagem nao carrega segredo").isFalse();
        assertThat(listing.get(0).get("id").asString()).matches("[0-9]+");
    }

    @Test
    @DisplayName("revogar corta o acesso no mesmo instante")
    void revokingCutsAccessImmediately() throws Exception {
        String secret = issueToken("token a revogar");
        assertThat(loginWith(secret).getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .as("antes de revogar, a credencial da aplicacao e aceita").isNull();
        String tokenId = listTokens().get(0).get("id").asString();

        MvcResult revoked = mockMvc.perform(
                        delete("/api/services/" + applicationId + "/tokens/" + tokenId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();

        assertThat(revoked.getResponse().getStatus()).isEqualTo(204);
        assertThat(loginWith(secret).getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .as("depois de revogar, a credencial da aplicacao e recusada").startsWith("Bearer");
    }

    @Test
    @DisplayName("revogar duas vezes responde igual")
    void revokingTwiceAnswersAlike() throws Exception {
        issueToken("token idempotente");
        String tokenId = listTokens().get(0).get("id").asString();
        String path = "/api/services/" + applicationId + "/tokens/" + tokenId;

        int first = mockMvc.perform(delete(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();
        int second = mockMvc.perform(delete(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(first).isEqualTo(204);
        assertThat(second).isEqualTo(204);
    }

    // 403 confirmaria que o token existe em outro lugar. 404 e a mesma resposta de
    // identificador inventado.
    @Test
    @DisplayName("token de outra aplicacao responde como inexistente")
    void tokenOfAnotherApplicationAnswersAsNotFound() throws Exception {
        issueToken("token do vizinho");
        String tokenId = listTokens().get(0).get("id").asString();
        String neighbour = registerApplication("vizinha-" + NEXT.incrementAndGet());

        int status = mockMvc.perform(delete("/api/services/" + neighbour + "/tokens/" + tokenId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(404);
    }

    @Test
    @DisplayName("credencial de aplicacao nao lista nem revoga token")
    void applicationCredentialNeitherListsNorRevokes() throws Exception {
        String secret = issueToken("token proprio");
        String tokenId = listTokens().get(0).get("id").asString();

        int listing = mockMvc.perform(get("/api/services/" + applicationId + "/tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secret))
                .andReturn().getResponse().getStatus();
        int revocation = mockMvc.perform(
                        delete("/api/services/" + applicationId + "/tokens/" + tokenId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secret))
                .andReturn().getResponse().getStatus();

        assertThat(listing).isIn(401, 403);
        assertThat(revocation).isIn(401, 403);
    }
}
