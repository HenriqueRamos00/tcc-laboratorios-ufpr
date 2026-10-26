package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova o isolamento entre aplicacoes nas rotas de leitura e manutencao.
 *
 * A regra que esta classe protege e a que o resto do servico depende: recurso de outra
 * aplicacao responde **404**, igual a identificador inventado. 403 ali confirmaria que o
 * recurso existe em algum lugar, e e assim que uma listagem vira censo da base alheia.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.notification.record-messages=true")
class ResourceScopingContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(200_000L);
    private static final String PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordedMessages recordedMessages;

    private String administrativeToken;

    private record Tenant(String applicationId, String token, String code) {
    }

    private Tenant first;
    private Tenant second;

    @BeforeEach
    void seed() throws Exception {
        recordedMessages.clear();
        String email = "admin.escopo" + NEXT.incrementAndGet() + "@lactec.exemplo";
        jdbcClient.sql("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador', :email, :email, TRUE, :hash, :now, :now)")
                .param("id", NEXT.incrementAndGet()).param("email", email)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .param("now", OffsetDateTime.now()).update();
        MvcResult signIn = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        administrativeToken = JSON.readTree(signIn.getResponse().getContentAsString())
                .get("accessToken").asString();
        first = tenant("escopo-a-" + NEXT.incrementAndGet());
        second = tenant("escopo-b-" + NEXT.incrementAndGet());
    }

    private Tenant tenant(String code) throws Exception {
        MvcResult application = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + code + "\",\"code\":\"" + code + "\"}"))
                .andReturn();
        String id = JSON.readTree(application.getResponse().getContentAsString()).get("id").asString();
        MvcResult issued = mockMvc.perform(post("/api/services/" + id + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"token\"}"))
                .andReturn();
        return new Tenant(id,
                JSON.readTree(issued.getResponse().getContentAsString()).get("accessToken").asString(),
                code);
    }

    private String createUser(Tenant tenant) throws Exception {
        String email = "pessoa" + NEXT.incrementAndGet() + "@exemplo.com";
        mockMvc.perform(post("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE",
                         "serviceId":"%s"}""".formatted(email, PASSWORD, tenant.applicationId())));
        JsonNode listing = JSON.readTree(mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode user : listing) {
            if (user.get("email").asString().equals(email)) {
                return user.get("id").asString();
            }
        }
        throw new IllegalStateException("conta recem criada nao apareceu na listagem");
    }

    @Test
    @DisplayName("a listagem da aplicacao mostra so as contas dela")
    void listingShowsOnlyTheAccountsOfTheCallingApplication() throws Exception {
        createUser(first);
        createUser(second);

        JsonNode listing = JSON.readTree(mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getContentAsString());

        assertThat(listing).isNotEmpty();
        for (JsonNode user : listing) {
            assertThat(user.get("serviceId").asString()).isEqualTo(first.applicationId());
        }
    }

    @Test
    @DisplayName("conta de outra aplicacao responde como inexistente, nao como proibida")
    void accountOfAnotherApplicationAnswersAsNotFound() throws Exception {
        String foreignAccount = createUser(second);

        int read = mockMvc.perform(get("/api/users/" + foreignAccount)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getStatus();
        int removed = mockMvc.perform(delete("/api/users/" + foreignAccount)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getStatus();
        int invented = mockMvc.perform(get("/api/users/999999999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getStatus();

        assertThat(read).isEqualTo(404);
        assertThat(removed).isEqualTo(404);
        assertThat(invented).as("identificador inventado responde igual").isEqualTo(read);
    }

    @Test
    @DisplayName("o administrador enxerga contas de todas as aplicacoes")
    void administratorSeesAccountsAcrossApplications() throws Exception {
        createUser(first);
        createUser(second);

        JsonNode listing = JSON.readTree(mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getContentAsString());

        assertThat(listing.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("desativar a conta impede a entrada")
    void deactivatingTheAccountPreventsSignIn() throws Exception {
        String accountId = createUser(first);
        String email = JSON.readTree(mockMvc.perform(get("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getContentAsString()).get("email").asString();
        mockMvc.perform(post("/api/auth/confirmations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + recordedMessages.lastTokenFor(email) + "\"}"));

        int updated = mockMvc.perform(put("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa Desativada","email":"%s","role":"CLIENTE",
                                 "active":false,"serviceId":"%s"}"""
                                .formatted(email, first.applicationId())))
                .andReturn().getResponse().getStatus();

        assertThat(updated).isEqualTo(200);
        assertThat(mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    // Excluir aplicacao e desativacao logica: apagar a linha deixaria conta e trilha
    // apontando para o nada, e a trilha precisa continuar explicavel.
    @Test
    @DisplayName("excluir a aplicacao desativa e corta o acesso dela na hora")
    void deletingTheApplicationDeactivatesItAndCutsAccess() throws Exception {
        int removed = mockMvc.perform(delete("/api/services/" + first.applicationId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(removed).isEqualTo(204);
        MvcResult afterwards = mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn();
        assertThat(afterwards.getResponse().getStatus()).isEqualTo(401);
        assertThat(afterwards.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer");

        JsonNode listing = JSON.readTree(mockMvc.perform(get("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getContentAsString());
        boolean stillListed = false;
        for (JsonNode application : listing) {
            if (application.get("id").asString().equals(first.applicationId())) {
                stillListed = true;
                assertThat(application.get("active").asBoolean()).isFalse();
            }
        }
        assertThat(stillListed).as("a aplicacao continua no cadastro, desativada").isTrue();
    }

    @Test
    @DisplayName("credencial de aplicacao nao administra aplicacao nem administrador")
    void applicationCredentialDoesNotAdminister() throws Exception {
        int services = mockMvc.perform(get("/api/services")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getStatus();
        int admins = mockMvc.perform(get("/api/admins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andReturn().getResponse().getStatus();

        assertThat(services).isIn(401, 403);
        assertThat(admins).isIn(401, 403);
    }
}
