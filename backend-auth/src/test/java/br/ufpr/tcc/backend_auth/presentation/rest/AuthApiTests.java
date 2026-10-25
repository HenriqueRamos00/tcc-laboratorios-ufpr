package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests extends ServiceTestSupport {
    private static final String PASSWORD = "Strong password 123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired SessaoAdministrativaRepository sessoes;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void cleanUsers() {
        prepararServico();
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @ParameterizedTest
    @EnumSource(PerfilUsuario.class)
    void loginReturnsOnlyIdentityForEachRole(PerfilUsuario role) throws Exception {
        Usuario usuario = saveUser("user@example.test", role, true);
        var result = mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON)
                        .content(login("  USER@EXAMPLE.TEST  ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Usuário de Teste"))
                .andExpect(jsonPath("$.email").value("user@example.test"))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.serviceCode").value("portal-web"))
                .andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email", "role", "serviceCode", "authorizations");
        // An identity response establishes no authenticated session.
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(newUser("client@example.test", PASSWORD, "CLIENTE")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong-password", "inactive"})
    void credentialFailuresShareOneResponse(String scenario) throws Exception {
        if (!scenario.equals("missing")) {
            saveUser("user@example.test", PerfilUsuario.CLIENTE, !scenario.equals("inactive"));
        }
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON)
                        .content(login("user@example.test", scenario.equals("wrong-password") ? "wrong" : PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"message\":\"Credenciais inválidas\"}"));
    }

    @Test
    void onlyCurrentActiveAdminTokenCanCreateUsers() throws Exception {
        Usuario admin = saveAdmin();
        String request = newUser("client@example.test", PASSWORD, "CLIENTE");
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(post("/api/users").with(httpBasic(admin.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        RequestPostProcessor token = bearerAuth("  ADMIN@EXAMPLE.TEST  ", PASSWORD);
        mvc.perform(post("/api/users").with(token).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("client@example.test", PASSWORD)))
                .andExpect(status().isUnauthorized());
        admin.setAdministradorServico(false);
        usuarios.saveAndFlush(admin);
        mvc.perform(post("/api/users").with(token).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        admin.setAdministradorServico(true);
        admin.setAtivo(false);
        usuarios.saveAndFlush(admin);
        mvc.perform(post("/api/users").with(token).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(PerfilUsuario.class)
    void adminCanCreatePortalUsersWithNormalizedEmailAndHashedPassword(PerfilUsuario role) throws Exception {
        saveAdmin();
        var response = mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUser("  NEW@EXAMPLE.TEST  ", PASSWORD, role.name())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Nova Conta"))
                .andExpect(jsonPath("$.email").value("new@example.test"))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email", "role", "serviceId", "companyIds", "authorizations");
        Usuario created = usuarios.findByEmail("new@example.test").orElseThrow();
        assertThat(created.isAtivo()).isTrue();
        assertThat(created.getSenhaHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(encoder.matches(PASSWORD, created.getSenhaHash())).isTrue();
    }

    @Test
    void emailIsUniqueAcrossServiceAndPortalAccounts() throws Exception {
        saveAdmin();
        mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUser(" ADMIN@EXAMPLE.TEST ", PASSWORD, "CLIENTE")))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"message\":\"E-mail já cadastrado\"}"));
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void concurrentCreationReturnsOneCreatedAndOneConflict() throws Exception {
        saveAdmin();
        String body = newUser("race@example.test", PASSWORD, "CLIENTE");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> create = () -> {
            start.await();
            return mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(usuarios.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "           ", "ééééééééééééééééééééééééééééééééééééé", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void creationRejectsInvalidPasswords(String password) throws Exception {
        saveAdmin();
        mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUser("new@example.test", password, "CLIENTE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void passwordBoundariesAndWhitespaceArePreserved() throws Exception {
        saveAdmin();
        List<String> passwords = List.of("Valid123", "Aa1" + "a".repeat(69), "Aa1" + "é".repeat(34) + "a", "  Password 123  ");
        for (int index = 0; index < passwords.size(); index++) {
            String email = "boundary" + index + "@example.test";
            String password = passwords.get(index);
            mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON).content(newUser(email, password, "CLIENTE")))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(login(email, password)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON)
                        .content(login("boundary3@example.test", "Password 123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedInputAndUnknownOrNumericRolesAreRejected() throws Exception {
        saveAdmin();
        for (String role : List.of("ADMIN", "TECNICO", "FUTURE_ROLE", "0", "admin")) {
            mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON).content(newUser("new@example.test", PASSWORD, role)))
                    .andExpect(status().isBadRequest());
        }
        String numericRole = newUser("new@example.test", PASSWORD, "CLIENTE").replace("\"CLIENTE\"", "0");
        mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(numericRole))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{", "{}", login("invalid-email", PASSWORD), login("user@example.test", "é".repeat(37)))) {
            mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("admin@example.test", "é".repeat(37))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creationValidatesRequiredFieldsAndLengthsAfterNormalization() throws Exception {
        saveAdmin();
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("name", " ", "email", "x@example.test", "password", PASSWORD, "role", "CLIENTE"),
                Map.<String, Object>of("name", "x".repeat(151), "email", "x@example.test", "password", PASSWORD, "role", "CLIENTE"),
                Map.<String, Object>of("name", "Nome", "email", "bad", "password", PASSWORD, "role", "CLIENTE"),
                Map.<String, Object>of("name", "Nome", "email", "x@example.test", "password", PASSWORD))) {
            mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(withService(body))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(usuarios.count()).isEqualTo(1);
    }

    private Map<String, Object> withService(Map<String, Object> body) {
        var request = new java.util.LinkedHashMap<>(body);
        request.put("serviceId", servico.getId().toString());
        return request;
    }

    private RequestPostProcessor bearerAuth(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        String response = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("accessToken").asText();
        return request -> { request.addHeader("Authorization", "Bearer " + token); return request; };
    }

    private String login(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String newUser(String email, String password, String role) {
        return json.writeValueAsString(Map.of("name", "  Nova Conta  ", "email", email, "password", password, "role", role, "serviceId", servico.getId().toString()));
    }

    private Usuario saveAdmin() {
        Usuario admin = saveUser("admin@example.test", null, true);
        admin.setAdministradorServico(true);
        return usuarios.saveAndFlush(admin);
    }

    private Usuario saveUser(String email, PerfilUsuario role, boolean active) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário de Teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(PASSWORD));
        usuario.setPerfil(role);
        if (role != null) usuario.setServico(servico);
        usuario.setAtivo(active);
        return usuarios.saveAndFlush(usuario);
    }
}
