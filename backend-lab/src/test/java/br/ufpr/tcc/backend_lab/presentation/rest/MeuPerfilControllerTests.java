package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.LoginResponse;
import br.ufpr.tcc.backend_lab.application.usecase.LoginUseCase;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.AlteracaoEmailRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.mail.host=smtp.test",
        "spring.mail.username=mailer@example.test",
        "spring.mail.password=teste",
        "spring.mail.from=mailer@example.test",
        "account.email-change.internal-link=http://localhost:4201/confirmar-alteracao-email?token={token}",
        "account.email-change.client-link=http://localhost:4200/confirmar-alteracao-email?token={token}"
})
@AutoConfigureMockMvc
@Import(MeuPerfilControllerTests.EmailTestConfiguration.class)
class MeuPerfilControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private AlteracaoEmailRepository alteracoesEmail;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private LoginUseCase login;
    @Autowired private RecordingMailSender mailSender;

    @BeforeEach
    void limpaMensagensEnviadas() {
        mailSender.reset();
    }

    @Test
    void aceitaPrefixoBearerEmMinusculas() throws Exception {
        Usuario tecnico = conta(PerfilUsuario.TECNICO);
        mockMvc.perform(get("/api/auth/internal/profile")
                        .header("Authorization", "bearer " + jwtService.generateToken(tecnico)))
                .andExpect(status().isOk());
    }

    @Test
    void rejeitaCabecalhoBearerSemToken() throws Exception {
        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tecnicoConsultaEEditaSomenteOsDadosPessoaisPermitidos() throws Exception {
        Usuario tecnico = conta(PerfilUsuario.TECNICO);
        String token = jwtService.generateToken(tecnico);

        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("Nome original"))
                .andExpect(jsonPath("$.role").value("TECNICO"))
                .andExpect(jsonPath("$.unit").value("Unidade A"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.editableFields[0]").value("name"))
                .andExpect(jsonPath("$.actions")
                        .value(org.hamcrest.Matchers.contains("change-email", "change-password")))
                .andExpect(jsonPath("$.passwordPolicy.maxUtf8Bytes").value(72));

        mockMvc.perform(patch("/api/auth/internal/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Nome atualizado  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome atualizado"));

        mockMvc.perform(patch("/api/auth/internal/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outro nome\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-profile"));

        Usuario armazenado = usuarios.findById(tecnico.getId()).orElseThrow();
        assertEquals("Nome atualizado", armazenado.getNome());
        assertEquals(PerfilUsuario.TECNICO, armazenado.getPerfil());
    }

    @Test
    void trocaSenhaRevogaTodasAsSessoesEErroDeReautenticacaoPreservaASessao() throws Exception {
        Usuario admin = conta(PerfilUsuario.ADMIN);
        String primeiraSessao = bearer(jwtService.generateToken(admin));
        String segundaSessao = bearer(jwtService.generateToken(admin));

        mockMvc.perform(post("/api/auth/internal/profile/password")
                        .header("Authorization", primeiraSessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha antiga\",\"newPassword\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-password"));

        mockMvc.perform(post("/api/auth/internal/profile/password")
                        .header("Authorization", primeiraSessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha antiga\",\"newPassword\":\"Nova senha 123!\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", primeiraSessao))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", segundaSessao))
                .andExpect(status().isUnauthorized());

        LoginResponse novaSessao = login.execute(new LoginRequest(admin.getEmail(), "Nova senha 123!"));
        mockMvc.perform(get("/api/auth/internal/profile")
                        .header("Authorization", bearer(novaSessao.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void senhaAtualIncorretaRetornaCodigoDeReautenticacaoSemEncerrarSessao() throws Exception {
        Usuario admin = conta(PerfilUsuario.ADMIN);
        String token = bearer(jwtService.generateToken(admin));

        mockMvc.perform(post("/api/auth/internal/profile/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorreta\",\"newPassword\":\"Nova senha 123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("current-password-incorrect"));

        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void trocaDeSenhaEEmailCompartilhamLimitePorConta() throws Exception {
        Usuario admin = conta(PerfilUsuario.ADMIN);
        String token = bearer(jwtService.generateToken(admin));
        String trocaSenha = "{\"currentPassword\":\"incorreta\",\"newPassword\":\"Nova senha 123!\"}";
        String trocaEmail = "{\"currentPassword\":\"incorreta\",\"newEmail\":\"novo@example.test\"}";

        for (int i = 0; i < 5; i++) {
            boolean senha = i % 2 == 0;
            mockMvc.perform(post("/api/auth/internal/profile/" + (senha ? "password" : "email-change"))
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(senha ? trocaSenha : trocaEmail))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("current-password-incorrect"));
        }

        for (String acao : List.of("password", "email-change")) {
            mockMvc.perform(post("/api/auth/internal/profile/" + acao)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content((acao.equals("password") ? trocaSenha : trocaEmail)
                                    .replace("incorreta", "senha antiga")))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("rate-limit-exceeded"));
        }

        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void trocaEmailExigeConfirmacaoDosDoisEnderecosERevogaSessoes() throws Exception {
        Usuario admin = conta(PerfilUsuario.ADMIN);
        String sessao = bearer(jwtService.generateToken(admin));
        String emailAtual = admin.getEmail();
        String emailNovo = "novo-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/auth/internal/profile/email-change")
                        .header("Authorization", sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"" + emailNovo
                                + "\",\"currentPassword\":\"senha antiga\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.newEmail").value(emailNovo))
                .andExpect(jsonPath("$.currentEmailConfirmed").value(false))
                .andExpect(jsonPath("$.newEmailConfirmed").value(false));

        assertEquals(2, mailSender.messages().size());
        String tokenAtual = mailSender.tokenPara(emailAtual);
        String tokenNovo = mailSender.tokenPara(emailNovo);
        var solicitacao = alteracoesEmail.findById(admin.getId()).orElseThrow();
        assertEquals(64, solicitacao.getTokenAtualHash().length());
        assertEquals(64, solicitacao.getTokenNovoHash().length());
        assertNotEquals(tokenAtual, solicitacao.getTokenAtualHash());
        assertNotEquals(tokenNovo, solicitacao.getTokenNovoHash());

        mockMvc.perform(post("/api/auth/internal/profile/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenNovo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("awaiting-other-confirmation"));
        assertEquals(emailAtual, usuarios.findById(admin.getId()).orElseThrow().getEmail());
        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", sessao))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/internal/profile/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenAtual + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        Usuario atualizado = usuarios.findById(admin.getId()).orElseThrow();
        assertEquals(emailNovo, atualizado.getEmail());
        assertEquals(1, atualizado.getCredentialVersion());
        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", sessao))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/internal/profile/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenAtual + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-email-change-token"));
        assertEquals(emailNovo, login.execute(new LoginRequest(emailNovo, "senha antiga")).user().email());
    }

    @Test
    void trocaDeSenhaInvalidaSolicitacaoPendenteDeEmail() throws Exception {
        Usuario admin = conta(PerfilUsuario.ADMIN);
        String sessao = bearer(jwtService.generateToken(admin));
        String emailNovo = "novo-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/auth/internal/profile/email-change")
                        .header("Authorization", sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"" + emailNovo
                                + "\",\"currentPassword\":\"senha antiga\"}"))
                .andExpect(status().isAccepted());
        String tokenAtual = mailSender.tokenPara(admin.getEmail());

        mockMvc.perform(post("/api/auth/internal/profile/password")
                        .header("Authorization", sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha antiga\","
                                + "\"newPassword\":\"Nova senha 123!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/internal/profile/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenAtual + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/internal/profile").header("Authorization", sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void falhaDeEnvioNaoMantemSolicitacaoPendente() throws Exception {
        Usuario admin = conta(PerfilUsuario.ADMIN);
        String emailNovo = "novo-" + UUID.randomUUID() + "@example.test";
        mailSender.failOnSend(true);

        mockMvc.perform(post("/api/auth/internal/profile/email-change")
                        .header("Authorization", bearer(jwtService.generateToken(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"" + emailNovo
                                + "\",\"currentPassword\":\"senha antiga\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("email-delivery-unavailable"));

        assertEquals(false, alteracoesEmail.findById(admin.getId()).isPresent());
    }

    private Usuario conta(PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome("Nome original");
        usuario.setEmail("perfil-" + UUID.randomUUID() + "@example.test");
        usuario.setSenhaHash(passwordEncoder.encode("senha antiga"));
        usuario.setPerfil(perfil);
        usuario.setUnidade("Unidade A");
        usuario.setEspecialidade("Metrologia");
        return usuarios.saveAndFlush(usuario);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class EmailTestConfiguration {
        @Bean
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    static class RecordingMailSender extends JavaMailSenderImpl {
        private final CopyOnWriteArrayList<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
        private volatile boolean failOnSend;

        @Override
        public void send(SimpleMailMessage message) {
            if (failOnSend) throw new MailSendException("Falha simulada no envio");
            sent.add(new SimpleMailMessage(message));
        }

        List<SimpleMailMessage> messages() {
            return List.copyOf(sent);
        }

        String tokenPara(String destinatario) {
            return sent.stream()
                    .filter(message -> Arrays.asList(message.getTo()).contains(destinatario))
                    .map(SimpleMailMessage::getText)
                    .map(RecordingMailSender::tokenFromBody)
                    .findFirst()
                    .orElseThrow();
        }

        void failOnSend(boolean fail) {
            failOnSend = fail;
        }

        void reset() {
            sent.clear();
            failOnSend = false;
        }

        private static String tokenFromBody(String body) {
            Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
            if (!matcher.find()) throw new AssertionError("O e-mail não contém o token esperado");
            return matcher.group(1);
        }
    }
}
