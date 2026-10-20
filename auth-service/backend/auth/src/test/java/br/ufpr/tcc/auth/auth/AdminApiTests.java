package br.ufpr.tcc.auth.auth;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.ufpr.tcc.auth.auth.model.Role;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTests {
    private static final String ADMIN_ID = "d4e9f39d-93f0-4e4f-83d8-61bd2bc7f6fd";
    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String PASSWORD = "long-enough-admin-password";
    private static final String SETUP_TOKEN = "test-setup-token-with-at-least-32-characters";
    private static final String CNPJ = "11222333000181";
    private static final String DATABASE_URL = "jdbc:h2:mem:admin-" + UUID.randomUUID()
            + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;

    @TestBean(methodName = "testKeys")
    JWKSource<SecurityContext> signingKeys;

    static JWKSource<SecurityContext> testKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM auth_user_company");
        jdbc.update("DELETE FROM api_key");
        jdbc.update("DELETE FROM auth_user");
        jdbc.update("DELETE FROM company");
    }

    @Test
    void csrfEndpointReturnsSessionToken() throws Exception {
        mvc.perform(get("/api/admin/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void setupCreatesFirstAdminOnlyOnce() throws Exception {
        mvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(post("/api/setup")
                        .header("X-Setup-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Administrador",
                                "email", ADMIN_EMAIL,
                                "password", PASSWORD))))
                .andExpect(status().isForbidden());
        assertThat(adminCount()).isZero();

        mvc.perform(post("/api/setup")
                        .header("X-Setup-Token", SETUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Administrador",
                                "email", ADMIN_EMAIL,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        assertThat(adminCount()).isEqualTo(1);

        mvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(false));

        mvc.perform(post("/api/setup")
                        .header("X-Setup-Token", SETUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Outro Administrador",
                                "email", "outro@example.test",
                                "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void adminLoginRejectsInvalidAndNonAdminAccounts() throws Exception {
        createAdmin();
        createUser("user@example.test", Role.USER, true);

        mvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", ADMIN_EMAIL, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "user@example.test", "password", PASSWORD))))
                .andExpect(status().isForbidden());

        MvcResult result = mvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", ADMIN_EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/admin/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    @Test
    void logoutInvalidatesAdminSession() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();

        mvc.perform(post("/api/admin/auth/logout")
                        .session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/admin/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRequireSessionAndCsrf() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/companies")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Empresa Exemplo", "cnpj", CNPJ))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/companies")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Empresa Exemplo", "cnpj", CNPJ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Empresa Exemplo"))
                .andExpect(jsonPath("$.cnpj").value(CNPJ))
                .andExpect(jsonPath("$.userCount").value(0));
    }

    @Test
    void userCrudAssignsCompaniesAndProtectsLastAdmin() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();
        String companyId = createCompany(session, "Empresa Alfa", CNPJ);

        JsonNode created = json.readTree(mvc.perform(post("/api/admin/users")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Usuário Portal",
                                "email", "portal@example.test",
                                "role", "USER",
                                "companyIds", List.of(companyId),
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.companies[0].id").value(companyId))
                .andReturn().getResponse().getContentAsString());
        String userId = created.path("id").asString();

        mvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + userId + "')]").isNotEmpty());

        mvc.perform(get("/api/admin/companies").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userCount").value(1));

        mvc.perform(put("/api/admin/users/" + userId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Usuário Atualizado",
                                "email", "portal@example.test",
                                "role", "USER",
                                "companyIds", List.of(companyId),
                                "password", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Usuário Atualizado"));

        mvc.perform(patch("/api/admin/users/" + userId + "/status")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(delete("/api/admin/users/" + userId)
                        .session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/admin/users/" + ADMIN_ID + "/status")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/admin/users/" + ADMIN_ID)
                        .session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void userRequiresCompanyAndCompanyCannotBeDeletedWhileLinked() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();
        String companyId = createCompany(session, "Empresa Beta", CNPJ);

        mvc.perform(post("/api/admin/users")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Sem Empresa",
                                "email", "sem-empresa@example.test",
                                "role", "USER",
                                "companyIds", List.of(),
                                "password", PASSWORD))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/users")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Com Empresa",
                                "email", "com-empresa@example.test",
                                "role", "USER",
                                "companyIds", List.of(companyId),
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/admin/companies/" + companyId)
                        .session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidAndDuplicateCnpjAreRejected() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();

        mvc.perform(post("/api/admin/companies")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Empresa Inválida", "cnpj", "11111111111111"))))
                .andExpect(status().isBadRequest());

        createCompany(session, "Empresa Válida", CNPJ);

        mvc.perform(post("/api/admin/companies")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Empresa Duplicada", "cnpj", CNPJ))))
                .andExpect(status().isConflict());
    }

    @Test
    void apiKeysAuthorizeIntegrationEndpointsAndCanBeRevoked() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();

        JsonNode created = json.readTree(mvc.perform(post("/api/admin/api-keys")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Serviço de Integração"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").isNotEmpty())
                .andExpect(jsonPath("$.apiKey.keyPrefix").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        String rawKey = created.path("key").asString();
        String keyId = created.path("apiKey").path("id").asString();
        assertThat(rawKey).startsWith("auth_");

        mvc.perform(get("/api/integrations/me").header("X-API-Key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Serviço de Integração"));

        mvc.perform(get("/api/admin/api-keys").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].key").doesNotExist());

        mvc.perform(patch("/api/admin/api-keys/" + keyId + "/status")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/integrations/me").header("X-API-Key", rawKey))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/admin/api-keys/" + keyId)
                        .session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/integrations/me").header("X-API-Key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredApiKeyIsRejected() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();
        JsonNode created = json.readTree(mvc.perform(post("/api/admin/api-keys")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Chave Expirada"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String rawKey = created.path("key").asString();
        String keyId = created.path("apiKey").path("id").asString();

        jdbc.update("UPDATE api_key SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), keyId);

        mvc.perform(get("/api/integrations/me").header("X-API-Key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAdminInvalidatesExistingSession() throws Exception {
        createAdmin();
        MockHttpSession session = loginAdmin();
        jdbc.update("UPDATE auth_user SET enabled = 0 WHERE id = ?", ADMIN_ID);

        mvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isUnauthorized());
    }

    private void createAdmin() {
        jdbc.update("""
                INSERT INTO auth_user (id, name, email, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                """, ADMIN_ID, "Administrador", ADMIN_EMAIL,
                passwords.encode(PASSWORD), "ADMIN");
    }

    private void createUser(String email, Role role, boolean enabled) {
        jdbc.update("""
                INSERT INTO auth_user (id, name, email, password_hash, role, enabled)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), "Usuário", email,
                passwords.encode(PASSWORD), role.name(), enabled);
    }

    private MockHttpSession loginAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", ADMIN_EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createCompany(MockHttpSession session, String name, String cnpj) throws Exception {
        String body = mvc.perform(post("/api/admin/companies")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "cnpj", cnpj))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asString();
    }

    private int adminCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE role = 'ADMIN'", Integer.class);
        return count == null ? 0 : count;
    }
}
