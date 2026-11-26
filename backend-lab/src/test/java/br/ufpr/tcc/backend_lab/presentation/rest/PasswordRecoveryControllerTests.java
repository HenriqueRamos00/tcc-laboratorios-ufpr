package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.application.event.PasswordRecoveryEmailRequested;
import br.ufpr.tcc.backend_lab.domain.model.entity.PasswordRecoveryToken;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.PasswordRecoveryTokenRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = "auth.recovery.mail-enabled=false")
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class PasswordRecoveryControllerTests {
    @Autowired private MockMvc http;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordRecoveryTokenRepository tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwt;
    @Autowired private ApplicationEvents events;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"\"}", "{\"email\":\"invalido\"}"})
    @DisplayName("Rejeita solicitações sem e-mail válido")
    void rejeitaEmailInvalido(String body) throws Exception {
        http.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, tokens.count());
    }

    @Test
    @DisplayName("Responde da mesma forma para conta inexistente ou inativa, sem gerar token")
    void preservaRespostaUniforme() throws Exception {
        Usuario inativo = usuario(PerfilUsuario.TECNICO);
        inativo.setAtivo(false);
        usuarios.saveAndFlush(inativo);
        solicitar("inexistente@example.test");
        solicitar(inativo.getEmail());
        assertEquals(0, tokens.count());
        assertEquals(0, events.stream(PasswordRecoveryEmailRequested.class).count());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilUsuario.class, names = {"ADMIN", "TECNICO"})
    @DisplayName("Redefine a senha uma única vez e revoga a sessão anterior sem mudar perfil")
    void redefineSenhaERevogaSessao(PerfilUsuario perfil) throws Exception {
        Usuario user = usuario(perfil);
        String sessaoAnterior = jwt.generateToken(user);
        solicitar(user.getEmail().toUpperCase());
        String token = ultimoToken();
        PasswordRecoveryToken stored = tokens.findAll().getFirst();
        assertEquals(PasswordRecoveryToken.digest(token), stored.getTokenDigest());
        assertTrue(stored.getExpiresAt().isAfter(Instant.now().plusSeconds(3500)));
        assertTrue(stored.getExpiresAt().isBefore(Instant.now().plusSeconds(3601)));

        redefinir(token, "NovaSenha123", 204);
        assertTrue(encoder.matches("NovaSenha123", usuarios.findById(user.getId()).orElseThrow().getSenhaHash()));
        assertTrue(user.isAtivo());
        assertEquals(perfil, user.getPerfil());
        http.perform(get("/api/health/private-probe")
                        .header("Authorization", "Bearer " + sessaoAnterior))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/auth/internal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"NovaSenha123\"}"))
                .andExpect(status().isOk());
        redefinir(token, "OutraSenha123", 400);
        assertTrue(encoder.matches("NovaSenha123", user.getSenhaHash()));
    }

    @Test
    @DisplayName("Uma nova solicitação invalida o link anterior")
    void invalidaLinkAnterior() throws Exception {
        Usuario user = usuario(PerfilUsuario.TECNICO);
        solicitar(user.getEmail());
        String primeiro = ultimoToken();
        solicitar(user.getEmail());
        String segundo = ultimoToken();
        // Requisições HTTP separadas consultam um novo contexto de persistência.
        entityManager.flush();
        entityManager.clear();
        redefinir(primeiro, "NovaSenha123", 400);
        redefinir(segundo, "NovaSenha123", 204);
    }

    @Test
    @DisplayName("Recusa token expirado ou conta desativada sem alterar a senha")
    void recusaTokenExpiradoEContaDesativada() throws Exception {
        Usuario user = usuario(PerfilUsuario.TECNICO);
        var expired = PasswordRecoveryToken.issue(user, Instant.now().minusSeconds(1));
        tokens.saveAndFlush(expired.stored());
        redefinir(expired.presentedToken(), "NovaSenha123", 400);
        solicitar(user.getEmail());
        String token = ultimoToken();
        user.setAtivo(false);
        usuarios.saveAndFlush(user);
        redefinir(token, "NovaSenha123", 400);
        assertFalse(user.isAtivo());
        assertTrue(encoder.matches("SenhaAnterior123", user.getSenhaHash()));
    }

    private Usuario usuario(PerfilUsuario perfil) {
        Usuario user = new Usuario();
        user.setNome("Conta de teste");
        user.setEmail("recuperacao@example.test");
        user.setPerfil(perfil);
        user.setAtivo(true);
        user.setSenhaHash(encoder.encode("SenhaAnterior123"));
        return usuarios.saveAndFlush(user);
    }

    private void solicitar(String email) throws Exception {
        var result = http.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent()).andReturn();
        assertEquals("", result.getResponse().getContentAsString());
    }

    private String ultimoToken() {
        var event = events.stream(PasswordRecoveryEmailRequested.class).toList().getLast();
        return UriComponentsBuilder.fromUriString(event.recoveryLink())
                .build().getQueryParams().getFirst("token");
    }

    private void redefinir(String token, String password, int expected) throws Exception {
        http.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expected));
    }
}
