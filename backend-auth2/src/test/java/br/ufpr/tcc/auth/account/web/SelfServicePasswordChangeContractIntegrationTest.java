package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prova a troca de senha pelo autoatendimento.
 *
 * A troca exige a senha atual, e por isso a senha atual tem limite proprio de tentativas:
 * sem ele a rota viraria um jeito de adivinhar a senha de quem deixou a sessao aberta. A
 * troca sobe a versao da credencial, que e o que faz o portal derrubar as outras sessoes, e
 * mata os links pendentes da conta.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.notification.record-messages=true")
class SelfServicePasswordChangeContractIntegrationTest extends SelfServiceContractTestBase {

    @Test
    @DisplayName("com a senha atual certa, a nova passa a entrar, a antiga nao, e a versao sobe")
    void passwordChangesWithTheCurrentPassword() throws Exception {
        Account account = confirmedAccount();
        assertThat(sessionStateOf(account).get("credentialVersion").asLong()).isZero();

        MvcResult changed = changePassword(account, PASSWORD, NEW_PASSWORD);

        assertThat(statusOf(changed)).isEqualTo(204);
        assertThat(changed.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(loginStatus(account.email(), NEW_PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(account.email(), PASSWORD)).isEqualTo(401);
        assertThat(sessionStateOf(account).get("credentialVersion").asLong())
                .as("a versao nova derruba os JWT emitidos antes da troca").isEqualTo(1L);
    }

    @Test
    @DisplayName("senha atual errada e recusada, e a senha continua a mesma")
    void wrongCurrentPasswordIsRefused() throws Exception {
        Account account = confirmedAccount();

        MvcResult refused = changePassword(account, "SenhaErrada123", NEW_PASSWORD);

        assertThat(statusOf(refused)).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("current-password-incorrect");
        assertThat(loginStatus(account.email(), PASSWORD)).isEqualTo(200);
        assertThat(sessionStateOf(account).get("credentialVersion").asLong()).isZero();
    }

    @Test
    @DisplayName("senha nova fora da politica do autoatendimento e recusada")
    void newPasswordOutsideTheSelfServicePolicyIsRefused() throws Exception {
        Account account = confirmedAccount();

        MvcResult tooShort = changePassword(account, PASSWORD, "Curta123");
        MvcResult withoutUppercase = changePassword(account, PASSWORD, "semmaiuscula123");

        assertThat(statusOf(tooShort))
                .as("oito caracteres bastam no cadastro, mas nao aqui").isEqualTo(400);
        assertThat(codeOf(tooShort)).isEqualTo("invalid-password");
        assertThat(statusOf(withoutUppercase)).isEqualTo(400);
        assertThat(codeOf(withoutUppercase)).isEqualTo("invalid-password");
        assertThat(loginStatus(account.email(), PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("cinco erros da senha atual bloqueiam a troca, inclusive com a senha certa")
    void fiveWrongCurrentPasswordsLockTheChange() throws Exception {
        Account account = confirmedAccount();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(statusOf(changePassword(account, "SenhaErrada123", NEW_PASSWORD)))
                    .isEqualTo(400);
        }

        MvcResult locked = changePassword(account, PASSWORD, NEW_PASSWORD);

        assertThat(statusOf(locked)).isEqualTo(429);
        assertThat(codeOf(locked)).isEqualTo("rate-limit-exceeded");
        assertThat(Long.parseLong(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isBetween(1L, 900L);
        assertThat(loginStatus(account.email(), NEW_PASSWORD))
                .as("o bloqueio impediu a troca").isEqualTo(401);
    }

    @Test
    @DisplayName("o bloqueio da senha atual vale tambem para a troca de e-mail")
    void currentPasswordLockAlsoCoversTheEmailChange() throws Exception {
        Account account = confirmedAccount();
        for (int attempt = 0; attempt < 5; attempt++) {
            changePassword(account, "SenhaErrada123", NEW_PASSWORD);
        }

        MvcResult emailChange = requestEmailChange(account, uniqueEmail(), PASSWORD);

        assertThat(statusOf(emailChange)).isEqualTo(429);
        assertThat(codeOf(emailChange)).isEqualTo("rate-limit-exceeded");
    }

    @Test
    @DisplayName("trocar a senha derruba o link de recuperacao pendente")
    void passwordChangeKillsThePendingRecoveryLink() throws Exception {
        Account account = confirmedAccount();
        asApplication(post("/api/auth/recoveries"), "{\"email\":\"" + account.email() + "\"}");
        String recoveryLink = recordedMessages.lastTokenFor(account.email());

        assertThat(statusOf(changePassword(account, PASSWORD, NEW_PASSWORD))).isEqualTo(204);

        MvcResult reset = asApplication(post("/api/auth/password-resets"),
                "{\"token\":\"" + recoveryLink + "\",\"password\":\"MaisUmaSenha789\"}");
        assertThat(statusOf(reset)).isEqualTo(400);
        assertThat(loginStatus(account.email(), NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("conta de outra aplicacao responde como inexistente, e a senha nao muda")
    void accountOfAnotherApplicationAnswersAsMissing() throws Exception {
        Account account = confirmedAccount();

        MvcResult refused = as(anotherApplicationToken(), post(pathOf(account, "/password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD
                        + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"));

        assertThat(statusOf(refused)).isEqualTo(404);
        assertThat(loginStatus(account.email(), PASSWORD)).isEqualTo(200);
    }
}
