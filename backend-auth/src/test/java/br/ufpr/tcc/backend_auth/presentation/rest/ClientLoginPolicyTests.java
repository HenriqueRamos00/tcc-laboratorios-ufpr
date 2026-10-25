package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarTokenServicoRequest;
import br.ufpr.tcc.backend_auth.application.service.BloqueioLoginClienteService;
import br.ufpr.tcc.backend_auth.domain.exception.LoginBloqueadoException;
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
class ClientLoginPolicyTests extends ServiceTestSupport {
    private static final String PASSWORD = "Valid123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    Usuario client;
    String adminToken;

    @BeforeEach
    void prepare() throws Exception {
        prepararServico();
        client = account("client@example.test", false, PASSWORD);
        account("admin@example.test", true, "Administrator 123!");
        String response = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin@example.test", "password", "Administrator 123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        adminToken = json.readTree(response).path("accessToken").asText();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abc1234", "abcdefgh", "ABCDEFGH", "12345678", "Abcdefgh", "abcdefg1", "ABCDEFG1"})
    void creationAndPasswordUpdatesRequireEightCharactersAndAllThreeCharacterClasses(String password) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>(clientRequest("new@example.test"));
        request.put("password", password);
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        request.put("email", client.getEmail());
        mvc.perform(put("/api/users/" + client.getId()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        login(client.getEmail(), PASSWORD, serviceToken, 200);
    }

    @Test
    void eightCharacterAndUnicodePasswordsAreAcceptedButUtf8LimitStillApplies() throws Exception {
        for (String password : List.of("Newpass1", "Ééééééé1", "Aa1" + "a".repeat(69), "Aa1" + "é".repeat(34) + "a", "  Pass123  ")) {
            var request = new java.util.LinkedHashMap<>(clientRequest(client.getEmail()));
            request.put("password", password);
            mvc.perform(put("/api/users/" + client.getId()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request)))
                    .andExpect(status().isOk());
            login(client.getEmail(), password, serviceToken, 200);
        }
        var oversized = new java.util.LinkedHashMap<>(clientRequest(client.getEmail()));
        oversized.put("password", "Aa1" + "é".repeat(35));
        mvc.perform(put("/api/users/" + client.getId()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(oversized)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fifthFailureStartsPersistentCooldownAndCorrectCredentialsDoNotBypassOrExtendIt() throws Exception {
        failFour();
        mvc.perform(loginRequest(client.getEmail(), "wrong", serviceToken))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.message").isString());
        Usuario blocked = stored();
        assertThat(blocked.getFalhasLogin()).isEqualTo(5);
        assertThat(blocked.getLoginBloqueadoAte()).isAfter(Instant.now().plusSeconds(895));
        assertThatThrownBy(() -> new BloqueioLoginClienteService(900).verificar(blocked, Instant.now()))
                .isInstanceOf(LoginBloqueadoException.class);
        login(client.getEmail(), PASSWORD, serviceToken, 429);
        login(client.getEmail(), "wrong", serviceToken, 429);
        assertThat(stored().getFalhasLogin()).isEqualTo(5);
        assertThat(stored().getLoginBloqueadoAte()).isEqualTo(blocked.getLoginBloqueadoAte());
    }

    @Test
    void successAndElapsedCooldownResetFailureBudget() throws Exception {
        failFour();
        login(client.getEmail(), PASSWORD, serviceToken, 200);
        assertThat(stored().getFalhasLogin()).isZero();
        failFour();
        login(client.getEmail(), "wrong", serviceToken, 429);
        Usuario blocked = stored();
        blocked.setLoginBloqueadoAte(Instant.now().minusSeconds(1));
        usuarios.saveAndFlush(blocked);
        login(client.getEmail(), "wrong", serviceToken, 401);
        assertThat(stored().getFalhasLogin()).isEqualTo(1);
        assertThat(stored().getLoginBloqueadoAte()).isNull();
        login(client.getEmail(), PASSWORD, serviceToken, 200);
        assertThat(stored().getFalhasLogin()).isZero();
    }

    @Test
    void concurrentRequestsCannotSkipTheFiveFailureLimit() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
                start.await();
                return mvc.perform(loginRequest(client.getEmail(), "wrong", serviceToken)).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            var statuses = new java.util.ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(401, 401, 401, 401, 429, 429, 429, 429);
        }
        assertThat(stored().getFalhasLogin()).isEqualTo(5);
        login(client.getEmail(), PASSWORD, serviceToken, 429);
    }

    @Test
    void forwardedIpAndRotatingServiceTokensDoNotChangeAccountScope() throws Exception {
        Usuario other = account("other@example.test", false, PASSWORD);
        failFour();
        String rotated = emissorTokens.emitir(servico.getId(), new CriarTokenServicoRequest("Outro token", null)).accessToken();
        mvc.perform(loginRequest(client.getEmail(), "wrong", rotated).header("X-Forwarded-For", "203.0.113.15"))
                .andExpect(status().isTooManyRequests());
        // Same portal proxy/IP, independent account budget.
        mvc.perform(loginRequest(other.getEmail(), PASSWORD, serviceToken).header("X-Forwarded-For", "203.0.113.15"))
                .andExpect(status().isOk());
        assertThat(usuarios.findById(other.getId()).orElseThrow().getFalhasLogin()).isZero();
    }

    @Test
    void anotherServiceAndInvalidTokensCannotExhaustClientBudget() throws Exception {
        Servico other = new Servico();
        other.setNome("Outro");
        other.setCodigo("other");
        other = servicos.saveAndFlush(other);
        String token = emissorTokens.emitir(other.getId(), new CriarTokenServicoRequest("Outro", null)).accessToken();
        for (int i = 0; i < 6; i++) {
            login(client.getEmail(), "wrong", token, 401);
            login(client.getEmail(), "wrong", "invalid", 401);
            login("missing@example.test", "wrong", serviceToken, 401);
        }
        assertThat(stored().getFalhasLogin()).isZero();
        login(client.getEmail(), PASSWORD, serviceToken, 200);
    }

    @Test
    void changingClientPasswordClearsCooldownAndAdminsKeepTheirSeparatePolicy() throws Exception {
        failFour();
        login(client.getEmail(), "wrong", serviceToken, 429);
        var request = new java.util.LinkedHashMap<>(clientRequest(client.getEmail()));
        request.put("password", "Newpass1");
        mvc.perform(put("/api/users/" + client.getId()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
        assertThat(stored().getFalhasLogin()).isZero();
        assertThat(stored().getLoginBloqueadoAte()).isNull();
        login(client.getEmail(), "Newpass1", serviceToken, 200);
        mvc.perform(post("/api/admins").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Admin", "email", "newadmin@example.test", "password", PASSWORD))))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", "admin@example.test", "password", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin@example.test", "password", "Administrator 123!"))))
                .andExpect(status().isOk());
    }

    @Test
    void oldPasswordsRemainUsableUntilChangedAndMetadataEditsPreserveThem() throws Exception {
        Usuario legacy = account("legacy@example.test", false, "legacy password without digits");
        login(legacy.getEmail(), "legacy password without digits", serviceToken, 200);
        var request = new java.util.LinkedHashMap<>(clientRequest(legacy.getEmail()));
        request.remove("password");
        mvc.perform(put("/api/users/" + legacy.getId()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
        login(legacy.getEmail(), "legacy password without digits", serviceToken, 200);
    }

    private void failFour() throws Exception {
        for (int i = 0; i < 4; i++) login(client.getEmail(), "wrong", serviceToken, 401);
        assertThat(stored().getFalhasLogin()).isEqualTo(4);
    }

    private Usuario stored() {
        return usuarios.findById(client.getId()).orElseThrow();
    }

    private void login(String email, String password, String token, int expected) throws Exception {
        mvc.perform(loginRequest(email, password, token)).andExpect(status().is(expected));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String email, String password, String token) {
        return post("/api/auth/login").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)));
    }

    private Map<String, Object> clientRequest(String email) {
        return Map.of("name", "Cliente", "email", email, "password", PASSWORD, "role", "CLIENTE", "active", true,
                "serviceId", servico.getId().toString());
    }

    private Usuario account(String email, boolean admin, String password) {
        Usuario user = new Usuario();
        user.setNome(email);
        user.setEmail(email);
        user.setSenhaHash(encoder.encode(password));
        user.setAdministradorServico(admin);
        if (!admin) {
            user.setPerfil(PerfilUsuario.CLIENTE);
            user.setServico(servico);
        }
        return usuarios.saveAndFlush(user);
    }
}
