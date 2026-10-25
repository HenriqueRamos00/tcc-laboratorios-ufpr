package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarTokenServicoRequest;
import br.ufpr.tcc.backend_auth.application.service.ServicoService;
import br.ufpr.tcc.backend_auth.application.service.TokenOpaco;
import br.ufpr.tcc.backend_auth.application.service.TokenServicoService;
import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceScopeTests extends ServiceTestSupport {
    private static final String PASSWORD = "  Client password 123!  ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired ServicoService cadastro;
    String adminToken;
    Usuario admin;
    Servico outro;
    String outroToken;

    @BeforeEach
    void prepare() throws Exception {
        prepararServico();
        admin = user("admin@example.test", null, true);
        outro = new Servico();
        outro.setNome("Laboratórios");
        outro.setCodigo("laboratorios");
        outro = servicos.saveAndFlush(outro);
        outroToken = emissorTokens.emitir(outro.getId(), new CriarTokenServicoRequest("Outro serviço", null)).accessToken();
        String body = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", admin.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        adminToken = json.readTree(body).path("accessToken").asText();
    }

    @Test
    void serviceTokenListsOnlyItsClientsAndCannotOverrideScope() throws Exception {
        Usuario own = user("portal@example.test", servico, false);
        user("other@example.test", outro, false);
        user("legacy@example.test", null, false);
        mvc.perform(as(get("/api/users").param("serviceId", outro.getId().toString()), serviceToken))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(own.getId().toString()))
                .andExpect(jsonPath("$[0].serviceId").value(servico.getId().toString()))
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist());
        mvc.perform(as(get("/api/users"), outroToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value("other@example.test"));
        mvc.perform(as(get("/api/users"), adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void crossServiceAndAdminIdsAreIndistinguishableFromMissingClients() throws Exception {
        Usuario own = user("portal@example.test", servico, false);
        Usuario other = user("other@example.test", outro, false);
        Usuario legacy = user("legacy@example.test", null, false);
        mvc.perform(as(get("/api/users/" + own.getId()), serviceToken)).andExpect(status().isOk());
        String missing = mvc.perform(as(get("/api/users/999999"), serviceToken)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        for (Long id : List.of(other.getId(), admin.getId(), legacy.getId())) {
            mvc.perform(as(get("/api/users/" + id), serviceToken)).andExpect(status().isNotFound()).andExpect(content().json(missing));
        }
    }

    @Test
    void loginRequiresServiceTokenAndChecksAssociationEvenForCorrectCredentials() throws Exception {
        Usuario client = user("portal@example.test", servico, false);
        String credentials = json.writeValueAsString(Map.of("email", " PORTAL@EXAMPLE.TEST ", "password", PASSWORD,
                "serviceId", outro.getId().toString()));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(post("/api/auth/login"), adminToken).contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/auth/login"), serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(client.getId().toString())).andExpect(jsonPath("$.serviceCode").value(servico.getCodigo()))
                .andExpect(jsonPath("$.serviceId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(as(post("/api/auth/login"), outroToken).contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isUnauthorized()).andExpect(content().json("{\"message\":\"Credenciais inválidas\"}"));
        user("legacy@example.test", null, false);
        assertLogin("legacy@example.test", serviceToken, 401);
        assertLogin(admin.getEmail(), serviceToken, 401);
        client.setAtivo(false);
        usuarios.saveAndFlush(client);
        assertLogin(client.getEmail(), serviceToken, 401);
    }

    @Test
    void serviceTokenCannotWriteUsersOrManageServicesTokensOrAdmins() throws Exception {
        for (String path : List.of("/api/admins", "/api/admins/1", "/api/services", "/api/services/" + servico.getId(),
                "/api/services/" + servico.getId() + "/tokens")) {
            mvc.perform(as(get(path), serviceToken)).andExpect(status().isForbidden());
        }
        for (String path : List.of("/api/users", "/api/admins", "/api/services", "/api/services/" + servico.getId() + "/tokens")) {
            mvc.perform(as(post(path), serviceToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of("/api/users/1", "/api/admins/1", "/api/services/" + servico.getId())) {
            mvc.perform(as(put(path), serviceToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(delete(path), serviceToken)).andExpect(status().isForbidden());
        }
        mvc.perform(as(delete("/api/services/" + servico.getId() + "/tokens/1"), serviceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousServiceManagementIsDeniedAndAdminCanUseEntireServiceCrud() throws Exception {
        for (String path : List.of("/api/services", "/api/services/1", "/api/services/1/tokens")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(path)).andExpect(status().isUnauthorized());
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        String response = mvc.perform(as(post("/api/services"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "  Novo serviço  ", "code", " NEW-SERVICE "))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("new-service"))
                .andExpect(jsonPath("$.name").value("Novo serviço")).andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).path("id").asText();
        mvc.perform(as(get("/api/services/" + id), adminToken)).andExpect(status().isOk());
        mvc.perform(as(get("/api/services"), adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(as(put("/api/services/" + id), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Renomeado", "code", "renamed", "active", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("renamed"));
        mvc.perform(as(delete("/api/services/" + id), adminToken)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/services/" + id), adminToken)).andExpect(status().isNotFound());
    }

    @Test
    void serviceCodesAreUniqueAndInvalidFieldsFailValidation() throws Exception {
        for (String code : List.of("", " ", "with space", "with_underscore", "-bad", "bad-", "a".repeat(81))) {
            mvc.perform(as(post("/api/services"), adminToken).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", "Serviço", "code", code))))
                    .andExpect(status().isBadRequest());
        }
        for (String name : List.of(" ", "a".repeat(151))) {
            mvc.perform(as(post("/api/services"), adminToken).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", name, "code", "valid"))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(as(post("/api/services"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Duplicado", "code", " PORTAL-WEB "))))
                .andExpect(status().isConflict());
        mvc.perform(as(put("/api/services/" + outro.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Duplicado", "code", "portal-web", "active", true))))
                .andExpect(status().isConflict());
        mvc.perform(as(put("/api/services/" + outro.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Serviço", "code", "valid"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateServiceCreationIsAlsoHandledDuringConcurrentInserts() throws Exception {
        String body = json.writeValueAsString(Map.of("name", "Concorrente", "code", "concurrent"));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return createService(body); });
            var b = executor.submit(() -> { start.await(); return createService(body); });
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void clientsRequireExistingActiveServiceAndLegacyAccountsRemainAdminOnlyUntilAssigned() throws Exception {
        Map<String, Object> client = new java.util.LinkedHashMap<>(client("new@example.test", servico.getId()));
        client.remove("serviceId");
        mvc.perform(as(post("/api/users"), adminToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(client)))
                .andExpect(status().isBadRequest());
        client.put("serviceId", "999999");
        mvc.perform(as(post("/api/users"), adminToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(client)))
                .andExpect(status().isNotFound());
        Usuario legacy = user("legacy@example.test", null, false);
        mvc.perform(as(get("/api/users/" + legacy.getId()), adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").isEmpty());
        mvc.perform(as(get("/api/users/" + legacy.getId()), serviceToken)).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/users/" + legacy.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(client(legacy.getEmail(), servico.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serviceId").value(servico.getId().toString()));
        assertLogin(legacy.getEmail(), serviceToken, 200);
    }

    @Test
    void reassignmentImmediatelyMovesVisibilityAndLoginToNewService() throws Exception {
        Usuario client = user("move@example.test", servico, false);
        mvc.perform(as(put("/api/users/" + client.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(client(client.getEmail(), outro.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serviceId").value(outro.getId().toString()));
        mvc.perform(as(get("/api/users/" + client.getId()), serviceToken)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/users"), serviceToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(get("/api/users/" + client.getId()), outroToken)).andExpect(status().isOk());
        assertLogin(client.getEmail(), serviceToken, 401);
        assertLogin(client.getEmail(), outroToken, 200);
    }

    @Test
    void tokenIssuanceReturnsSecretOnceStoresHashAndSurvivesNewServiceInstance() throws Exception {
        JsonNode issued = issueToken(servico.getId(), Map.of("name", "  C# backend  ", "expiresIn", 600));
        String secret = issued.path("accessToken").asText();
        assertThat(secret).matches("svc_[A-Za-z0-9_-]{43}").doesNotContain(".");
        assertThat(issued.path("expiresIn").asLong()).isEqualTo(600);
        assertThat(issued.path("token").path("name").asText()).isEqualTo("C# backend");
        var stored = tokensServico.findById(Long.valueOf(issued.path("token").path("id").asText())).orElseThrow();
        assertThat(stored.getTokenHash()).isEqualTo(TokenOpaco.hash(secret)).hasSize(64).isNotEqualTo(secret);
        assertThat(new TokenServicoService(tokensServico, cadastro, 2592000).autenticar(secret)).isEqualTo(servico.getId());
        String list = mvc.perform(as(get("/api/services/" + servico.getId() + "/tokens"), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(secret, stored.getTokenHash(), "accessToken", "tokenHash");
        assertThat(json.readTree(list).get(0).propertyNames()).containsExactlyInAnyOrder("id", "name", "serviceId", "createdAt", "expiresAt");
        assertThat(emissorTokens.emitir(servico.getId(), new CriarTokenServicoRequest("Log seguro", null)).toString())
                .doesNotContain("svc_");
    }

    @Test
    void expiredRevokedAndForgedTokensFailBothReadsAndLoginWhileOtherTokensKeepWorking() throws Exception {
        Usuario client = user("portal@example.test", servico, false);
        var stored = tokensServico.findByTokenHash(TokenOpaco.hash(serviceToken)).orElseThrow();
        stored.setExpiraEm(Instant.now().minusSeconds(1));
        tokensServico.saveAndFlush(stored);
        assertTokenDenied(serviceToken, client.getEmail());
        assertTokenDenied("svc_" + "x".repeat(43), client.getEmail());
        assertTokenDenied("svc_invalid", client.getEmail());
        JsonNode issued = issueToken(servico.getId(), Map.of("name", "Rotação"));
        String fresh = issued.path("accessToken").asText();
        assertThat(issued.path("expiresIn").asLong()).isEqualTo(2592000);
        String tokenId = issued.path("token").path("id").asText();
        mvc.perform(as(delete("/api/services/" + outro.getId() + "/tokens/" + tokenId), adminToken))
                .andExpect(status().isNotFound());
        assertLogin(client.getEmail(), fresh, 200);
        String surviving = issueToken(servico.getId(), Map.of("name", "Sobrevivente")).path("accessToken").asText();
        mvc.perform(as(delete("/api/services/" + servico.getId() + "/tokens/" + tokenId), adminToken))
                .andExpect(status().isNoContent());
        assertTokenDenied(fresh, client.getEmail());
        assertLogin(client.getEmail(), surviving, 200);
    }

    @Test
    void deactivationRevokesTokensPermanentlyAndBlocksIssuanceAndNewClients() throws Exception {
        Usuario client = user("portal@example.test", servico, false);
        setActive(false);
        assertTokenDenied(serviceToken, client.getEmail());
        mvc.perform(as(post("/api/services/" + servico.getId() + "/tokens"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bloqueado\"}"))
                .andExpect(status().isConflict());
        mvc.perform(as(post("/api/users"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(client("blocked@example.test", servico.getId()))))
                .andExpect(status().isConflict());
        assertThat(usuarios.findById(client.getId()).orElseThrow().isAtivo()).isTrue();
        setActive(true);
        assertTokenDenied(serviceToken, client.getEmail());
        String fresh = issueToken(servico.getId(), Map.of("name", "Novo token")).path("accessToken").asText();
        assertLogin(client.getEmail(), fresh, 200);
    }

    @Test
    void inactiveServiceStillAllowsAdminAccountEditsButRejectsNewAssociations() throws Exception {
        Usuario own = user("portal@example.test", servico, false);
        Usuario other = user("other@example.test", outro, false);
        setActive(false);
        mvc.perform(as(put("/api/users/" + own.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Editado", "email", own.getEmail(),
                                "role", "CLIENTE", "active", false, "serviceId", servico.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Editado"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(as(put("/api/users/" + other.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(client(other.getEmail(), servico.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingServiceWithClientsIsRejectedAndDeletingEmptyServiceRevokesTokens() throws Exception {
        Usuario client = user("portal@example.test", servico, false);
        mvc.perform(as(delete("/api/services/" + servico.getId()), adminToken)).andExpect(status().isConflict());
        mvc.perform(as(delete("/api/users/" + client.getId()), adminToken)).andExpect(status().isNoContent());
        mvc.perform(as(delete("/api/services/" + servico.getId()), adminToken)).andExpect(status().isNoContent());
        assertTokenDenied(serviceToken, client.getEmail());
        mvc.perform(as(get("/api/services/" + servico.getId() + "/tokens"), adminToken)).andExpect(status().isNotFound());
        assertThat(tokensServico.findByTokenHash(TokenOpaco.hash(serviceToken))).isEmpty();
    }

    @Test
    void invalidTokenLifetimeAndNamesAreRejected() throws Exception {
        String path = "/api/services/" + servico.getId() + "/tokens";
        for (Object value : List.of(0, -1, 31536001, "abc")) {
            mvc.perform(as(post(path), adminToken).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", "Token", "expiresIn", value))))
                    .andExpect(status().isBadRequest());
        }
        for (String name : List.of(" ", "a".repeat(151))) {
            mvc.perform(as(post(path), adminToken).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", name))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(as(post("/api/services/999999/tokens"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Token\"}"))
                .andExpect(status().isNotFound());
    }

    private void assertTokenDenied(String secret, String email) throws Exception {
        mvc.perform(as(get("/api/users"), secret)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer")).andExpect(jsonPath("$.message").value("Credenciais inválidas"));
        assertLogin(email, secret, 401);
    }

    private void setActive(boolean active) throws Exception {
        mvc.perform(as(put("/api/services/" + servico.getId()), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Portal", "code", "portal-web", "active", active))))
                .andExpect(status().isOk());
    }

    private int createService(String body) throws Exception {
        return mvc.perform(as(post("/api/services"), adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode issueToken(Long id, Map<String, Object> request) throws Exception {
        String response = mvc.perform(as(post("/api/services/" + id + "/tokens"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private void assertLogin(String email, String token, int expected) throws Exception {
        mvc.perform(as(post("/api/auth/login"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().is(expected)).andExpect(header().string("Cache-Control", "no-store"));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private Map<String, Object> client(String email, Long serviceId) {
        return Map.of("name", "Cliente", "email", email, "password", PASSWORD, "role", "CLIENTE", "active", true,
                "serviceId", serviceId.toString());
    }

    private Usuario user(String email, Servico service, boolean isAdmin) {
        Usuario user = new Usuario();
        user.setNome(email);
        user.setEmail(email);
        user.setSenhaHash(encoder.encode(PASSWORD));
        user.setPerfil(isAdmin ? null : PerfilUsuario.CLIENTE);
        user.setAdministradorServico(isAdmin);
        user.setServico(service);
        return usuarios.saveAndFlush(user);
    }
}
