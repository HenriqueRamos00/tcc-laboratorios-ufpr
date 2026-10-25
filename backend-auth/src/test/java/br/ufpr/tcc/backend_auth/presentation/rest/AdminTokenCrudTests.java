package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.application.service.ConfiguracaoInicialService;
import br.ufpr.tcc.backend_auth.application.service.TokenAdministrativoService;
import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
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
class AdminTokenCrudTests extends ServiceTestSupport {
    private static final String PASSWORD = "Original password 123!";
    private static final String NEW_PASSWORD = "  New password 456!  ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired SessaoAdministrativaRepository sessoes;
    @Autowired PasswordEncoder encoder;
    @Autowired ConfiguracaoInicialService setup;
    Usuario root;
    String token;

    @BeforeEach
    void prepare() throws Exception {
        prepararServico();
        root = account("root@example.test", true, null);
        token = login(root.getEmail(), PASSWORD);
    }

    @Test
    void adminLoginIssuesOpaqueExpiringTokenAndStoresOnlyItsHash() throws Exception {
        var session = sessoes.findAll().getFirst();
        assertThat(token).matches("[A-Za-z0-9_-]{43}").doesNotContain(".");
        assertThat(session.getTokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(token);
        assertThat(session.getExpiraEm()).isAfter(Instant.now().plusSeconds(3500));
        // A fresh service instance validates the stored token without an in-memory token registry.
        assertThat(new TokenAdministrativoService(sessoes, 3600).autenticar(token).id()).isEqualTo(root.getId().toString());
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        account("client@example.test", false, PerfilUsuario.CLIENTE);
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("client@example.test", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("client@example.test", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void expiredTokensFailAndDoNotPreventFreshAdminLogin() throws Exception {
        var session = sessoes.findAll().getFirst();
        session.setExpiraEm(Instant.now().minusSeconds(1));
        sessoes.saveAndFlush(session);
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(post("/api/admin/auth/login").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(root.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"));
        assertThat(sessoes.count()).isEqualTo(1);
        assertThat(sessoes.findById(session.getTokenHash())).isEmpty();
    }

    @Test
    void allManagementMethodsRequireBearerAuthentication() throws Exception {
        for (String path : new String[] {"/api/users", "/api/admins", "/api/users/1", "/api/admins/1"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer forged-token")).andExpect(status().isUnauthorized());
        }
        for (String path : new String[] {"/api/users/1", "/api/admins/1"}) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void portalUserCrudPreservesPasswordWhenOmittedAndReportsActiveStatus() throws Exception {
        JsonNode created = body(mvc.perform(auth(post("/api/users")).contentType(MediaType.APPLICATION_JSON)
                .content(clientRequest("Zulu", "client@example.test", PASSWORD, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = created.path("id").asText();
        String hash = usuarios.findById(Long.valueOf(id)).orElseThrow().getSenhaHash();
        mvc.perform(auth(get("/api/users"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].active").value(true));
        mvc.perform(auth(get("/api/users/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENTE")).andExpect(jsonPath("$.senhaHash").doesNotExist());
        mvc.perform(auth(get("/api/users/" + root.getId()))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/admins/" + id))).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(clientRequest("  Alice  ", " NEW@EXAMPLE.TEST ", null, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("new@example.test"));
        Usuario updated = usuarios.findById(Long.valueOf(id)).orElseThrow();
        assertThat(updated.getSenhaHash()).isEqualTo(hash);
        assertThat(updated.isAdministradorServico()).isFalse();
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials(updated.getEmail(), PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(clientRequest("Alice", updated.getEmail(), NEW_PASSWORD, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials(updated.getEmail(), NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(clientRequest("Alice", updated.getEmail(), null, true)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials(updated.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials(updated.getEmail(), NEW_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(auth(delete("/api/users/" + id))).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(auth(get("/api/users/" + id))).andExpect(status().isNotFound());
        assertThat(usuarios.findById(Long.valueOf(id))).isEmpty();
    }

    @Test
    void adminCrudDoesNotCreatePortalAccessAndDeletionRevokesTokens() throws Exception {
        String response = mvc.perform(auth(post("/api/admins")).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Another", "second@example.test", PASSWORD, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = body(response).path("id").asText();
        String secondToken = login("second@example.test", PASSWORD);
        String originalHash = usuarios.findById(Long.valueOf(id)).orElseThrow().getSenhaHash();
        mvc.perform(auth(get("/api/admins"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(auth(put("/api/admins/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Updated", "UPDATED@EXAMPLE.TEST", null, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("updated@example.test"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.role").doesNotExist());
        assertThat(usuarios.findById(Long.valueOf(id)).orElseThrow().getSenhaHash()).isEqualTo(originalHash);
        mvc.perform(get("/api/admins/" + id).header("Authorization", "Bearer " + secondToken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials("updated@example.test", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(delete("/api/admins/" + id))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/admins/" + id))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + secondToken)).andExpect(status().isUnauthorized());
        assertThat(usuarios.findById(Long.valueOf(id))).isEmpty();
        assertThat(setup.url("http://localhost")).isEmpty();
    }

    @Test
    void changingOwnAdminPasswordRevokesEveryExistingToken() throws Exception {
        String anotherToken = login(root.getEmail(), PASSWORD);
        mvc.perform(auth(put("/api/admins/" + root.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Root", root.getEmail(), NEW_PASSWORD, true)))
                .andExpect(status().isOk());
        for (String oldToken : List.of(token, anotherToken)) {
            mvc.perform(get("/api/admins").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        }
        assertThat(sessoes.count()).isZero();
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(root.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        String newToken = login(root.getEmail(), NEW_PASSWORD);
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
    }

    @Test
    void deactivationRevokesTokensAndReactivationDoesNotRestoreThem() throws Exception {
        Usuario second = account("second@example.test", true, null);
        String secondToken = login(second.getEmail(), PASSWORD);
        mvc.perform(auth(put("/api/admins/" + second.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Second", second.getEmail(), null, false)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + secondToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(second.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(put("/api/admins/" + second.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Second", second.getEmail(), null, true)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + secondToken)).andExpect(status().isUnauthorized());
        assertThat(login(second.getEmail(), PASSWORD)).isNotEqualTo(secondToken);
    }

    @Test
    void lastActiveAdminCannotBeDeletedOrDeactivatedFromEitherScope() throws Exception {
        mvc.perform(auth(delete("/api/admins/" + root.getId()))).andExpect(status().isConflict());
        mvc.perform(auth(put("/api/admins/" + root.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Root", root.getEmail(), null, false)))
                .andExpect(status().isConflict());
        root.setPerfil(PerfilUsuario.CLIENTE);
        usuarios.saveAndFlush(root);
        mvc.perform(auth(put("/api/users/" + root.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(clientRequest("Root", root.getEmail(), null, false)))
                .andExpect(status().isConflict());
        mvc.perform(auth(delete("/api/users/" + root.getId()))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/users/" + root.getId()))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/admins/" + root.getId()))).andExpect(status().isOk());
        Usuario retained = usuarios.findById(root.getId()).orElseThrow();
        assertThat(retained.isAtivo()).isTrue();
        assertThat(retained.isAdministradorServico()).isTrue();
        assertThat(retained.getPerfil()).isNull();
        assertThat(setup.url("http://localhost")).isEmpty();
    }

    @Test
    void deletingAdminCapacityPreservesExplicitPortalProfile() throws Exception {
        Usuario second = account("second@example.test", true, PerfilUsuario.CLIENTE);
        String secondToken = login(second.getEmail(), PASSWORD);
        mvc.perform(delete("/api/admins/" + second.getId()).header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + secondToken)).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/admins/" + second.getId()))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/users/" + second.getId()))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(credentials(second.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void updatesValidateFieldsAndEnforceEmailUniquenessAcrossScopes() throws Exception {
        Usuario client = account("client@example.test", false, PerfilUsuario.CLIENTE);
        mvc.perform(auth(put("/api/users/" + client.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(clientRequest("Client", " ROOT@EXAMPLE.TEST ", null, true)))
                .andExpect(status().isConflict());
        for (String request : List.of("{}", clientRequest(" ", client.getEmail(), null, true),
                clientRequest("Client", "invalid", null, true), clientRequest("Client", client.getEmail(), "short", true),
                clientRequest("Client", client.getEmail(), "é".repeat(37), true))) {
            mvc.perform(auth(put("/api/users/" + client.getId())).contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(auth(put("/api/admins/" + root.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("Root", client.getEmail(), null, true)))
                .andExpect(status().isConflict());
        for (String path : new String[] {"/api/users/not-an-id", "/api/admins/not-an-id"}) {
            mvc.perform(auth(get(path))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        for (String path : new String[] {"/api/users/999999", "/api/admins/999999"}) {
            mvc.perform(auth(get(path))).andExpect(status().isNotFound());
            mvc.perform(auth(delete(path))).andExpect(status().isNotFound());
        }
    }

    @Test
    void concurrentAdminDeletionLeavesOneActiveAdministrator() throws Exception {
        Usuario second = account("second@example.test", true, null);
        String secondToken = login(second.getEmail(), PASSWORD);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return deleteAdmin(root.getId(), token); });
            var b = executor.submit(() -> { start.await(); return deleteAdmin(second.getId(), secondToken); });
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 409);
        }
        assertThat(usuarios.findAllByAdministradorServicoTrueOrderByNomeAscIdAsc()).hasSize(1).allMatch(Usuario::isAtivo);
        assertThat(setup.url("http://localhost")).isEmpty();
    }

    private int deleteAdmin(Long id, String accessToken) throws Exception {
        return mvc.perform(delete("/api/admins/" + id).header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600)).andExpect(jsonPath("$.user.role").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn().getResponse().getContentAsString();
        return body(response).path("accessToken").asText();
    }

    private JsonNode body(String source) {
        return json.readTree(source);
    }

    private String credentials(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String adminRequest(String name, String email, String password, boolean active) {
        var value = new java.util.LinkedHashMap<String, Object>(Map.of("name", name, "email", email, "active", active));
        if (password != null) value.put("password", password);
        return json.writeValueAsString(value);
    }

    private String clientRequest(String name, String email, String password, boolean active) {
        var value = new java.util.LinkedHashMap<String, Object>(Map.of("name", name, "email", email, "role", "CLIENTE", "active", active, "serviceId", servico.getId().toString()));
        if (password != null) value.put("password", password);
        return json.writeValueAsString(value);
    }

    private Usuario account(String email, boolean admin, PerfilUsuario role) {
        Usuario user = new Usuario();
        user.setNome(email);
        user.setEmail(email);
        user.setSenhaHash(encoder.encode(PASSWORD));
        user.setAdministradorServico(admin);
        user.setPerfil(role);
        if (role != null) user.setServico(servico);
        return usuarios.saveAndFlush(user);
    }
}
