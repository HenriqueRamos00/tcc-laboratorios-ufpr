package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.application.service.ConfiguracaoInicialService;
import br.ufpr.tcc.backend_auth.application.usecase.RegistrarPrimeiroAdministradorUseCase;
import br.ufpr.tcc.backend_auth.domain.exception.ConfiguracaoInicialException;
import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class ConfiguracaoInicialTests {
    private static final String PASSWORD = "  Admin password 123!  ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired SessaoAdministrativaRepository sessoes;
    @Autowired PasswordEncoder encoder;
    @Autowired ConfiguracaoInicialService setup;
    @Autowired RegistrarPrimeiroAdministradorUseCase registrar;

    @BeforeEach
    void cleanUsers() {
        sessoes.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void generatedLinkOpensFormButMissingAndWrongTokensCannotRegister() throws Exception {
        URI url = URI.create(setup.url("http://localhost:8081/").orElseThrow());
        assertThat(url.getPath()).isEqualTo("/setup");
        assertThat(token()).hasSize(43);
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("method=\"post\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Criar administrador")));
        mvc.perform(get("/setup")).andExpect(status().isNotFound());
        mvc.perform(get("/setup").param("token", "incorrect")).andExpect(status().isNotFound());
        mvc.perform(post("/api/setup/admin").contentType(MediaType.APPLICATION_JSON)
                        .content(adminRequest("admin@example.test", PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/setup/admin").header("X-Setup-Token", "incorrect")
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest("admin@example.test", PASSWORD)))
                .andExpect(status().isNotFound());
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void firstAdminIsServiceOnlyAndSetupClosesAlsoAfterRestartOrDeactivation() throws Exception {
        String token = token();
        var response = mvc.perform(post("/api/setup/admin").header("X-Setup-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest(" ADMIN@EXAMPLE.TEST ", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Administrador"))
                .andExpect(jsonPath("$.email").value("admin@example.test"))
                .andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email");
        Usuario admin = usuarios.findByEmail("admin@example.test").orElseThrow();
        assertThat(admin.isAdministradorServico()).isTrue();
        assertThat(admin.getPerfil()).isNull();
        assertThat(encoder.matches(PASSWORD, admin.getSenhaHash())).isTrue();
        assertThat(encoder.matches(PASSWORD.trim(), admin.getSenhaHash())).isFalse();
        mvc.perform(get("/setup").param("token", token)).andExpect(status().isGone());
        mvc.perform(post("/api/setup/admin").header("X-Setup-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest("other@example.test", PASSWORD)))
                .andExpect(status().isGone());
        assertThat(new ConfiguracaoInicialService(usuarios, registrar).url("http://localhost")).isEmpty();
        admin.setAtivo(false);
        usuarios.saveAndFlush(admin);
        assertThat(setup.url("http://localhost")).isEmpty();
        mvc.perform(get("/setup").param("token", token)).andExpect(status().isGone());
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void adminLoginAndPortalLoginHaveIndependentPermissions() throws Exception {
        registerAdmin();
        String login = json.writeValueAsString(Map.of("email", "admin@example.test", "password", PASSWORD));
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isString()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());

        Usuario admin = usuarios.findByEmail("admin@example.test").orElseThrow();
        admin.setPerfil(PerfilUsuario.CLIENTE);
        usuarios.saveAndFlush(admin);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CLIENTE"));
        admin.setAdministradorServico(false);
        usuarios.saveAndFlush(admin);
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void serviceAdminCanCreateMoreAdminsButPortalRegistrationCannotGrantAdminPermission() throws Exception {
        registerAdmin();
        String request = adminRequest("second@example.test", PASSWORD);
        mvc.perform(post("/api/admins").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admins").with(bearerAuth("admin@example.test", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").doesNotExist());
        Usuario second = usuarios.findByEmail("second@example.test").orElseThrow();
        assertThat(second.isAdministradorServico()).isTrue();
        assertThat(second.getPerfil()).isNull();

        String client = json.writeValueAsString(Map.of("name", "Cliente", "email", "client@example.test",
                "password", PASSWORD, "role", "CLIENTE", "administradorServico", true));
        mvc.perform(post("/api/users").with(bearerAuth("admin@example.test", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(client))
                .andExpect(status().isCreated());
        assertThat(usuarios.findByEmail("client@example.test").orElseThrow().isAdministradorServico()).isFalse();
        mvc.perform(post("/api/admins").header("Authorization", "Bearer invalid-client-token")
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest("blocked@example.test", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "client@example.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingPortalUsersDoNotBlockSetupAndAreNeverPromoted() throws Exception {
        Usuario client = new Usuario();
        client.setNome("Cliente");
        client.setEmail("client@example.test");
        client.setSenhaHash(encoder.encode(PASSWORD));
        client.setPerfil(PerfilUsuario.CLIENTE);
        usuarios.saveAndFlush(client);
        String token = token();
        mvc.perform(post("/api/setup/admin").header("X-Setup-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest("client@example.test", PASSWORD)))
                .andExpect(status().isConflict());
        assertThat(usuarios.findByEmail("client@example.test").orElseThrow().isAdministradorServico()).isFalse();
        assertThat(setup.url("http://localhost")).isPresent();
        registerAdmin();
        assertThat(usuarios.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "            ", "ééééééééééééééééééééééééééééééééééééé"})
    void invalidPasswordLeavesLinkUsable(String password) throws Exception {
        String token = token();
        mvc.perform(post("/api/setup/admin").header("X-Setup-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest("admin@example.test", password)))
                .andExpect(status().isBadRequest());
        assertThat(usuarios.count()).isZero();
        mvc.perform(get("/setup").param("token", token)).andExpect(status().isOk());
    }

    @Test
    void restartBeforeSetupInvalidatesPreviousLink() {
        String oldToken = token();
        var restarted = new ConfiguracaoInicialService(usuarios, registrar);
        assertThatThrownBy(() -> restarted.validarAcesso(oldToken))
                .isInstanceOf(ConfiguracaoInicialException.class).hasMessage("Link de configuração inválido");
        assertThat(restarted.url("http://localhost")).isPresent();
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void concurrentSetupCreatesExactlyOneAdmin() throws Exception {
        String token = token();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> first = () -> setupRequest("first@example.test", token, start);
            Callable<Integer> second = () -> setupRequest("second@example.test", token, start);
            var a = executor.submit(first);
            var b = executor.submit(second);
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 410);
        }
        assertThat(usuarios.count()).isEqualTo(1);
    }

    private int setupRequest(String email, String token, CountDownLatch start) throws Exception {
        start.await();
        return mvc.perform(post("/api/setup/admin").header("X-Setup-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(adminRequest(email, PASSWORD)))
                .andReturn().getResponse().getStatus();
    }

    private RequestPostProcessor bearerAuth(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        String response = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("accessToken").asText();
        return request -> { request.addHeader("Authorization", "Bearer " + token); return request; };
    }

    private void registerAdmin() throws Exception {
        mvc.perform(post("/api/setup/admin").header("X-Setup-Token", token())
                        .contentType(MediaType.APPLICATION_JSON).content(adminRequest("admin@example.test", PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String token() {
        return URI.create(setup.url("http://localhost").orElseThrow()).getRawQuery().substring("token=".length());
    }

    private String adminRequest(String email, String password) {
        return json.writeValueAsString(Map.of("name", " Administrador ", "email", email, "password", password));
    }
}
