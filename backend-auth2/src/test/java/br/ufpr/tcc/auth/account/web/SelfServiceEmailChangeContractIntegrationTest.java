package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Prova a troca de e-mail pelo autoatendimento.
 *
 * A troca e confirmada nos dois enderecos: um link vai para o endereco atual e outro para o
 * novo, e o e-mail so muda depois dos dois. O link do endereco atual prova que quem pediu e o
 * dono da conta, e nao alguem com a sessao aberta; o do endereco novo prova que o endereco
 * existe e e de quem pediu. Qualquer mudanca de credencial no meio do caminho cancela a troca.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.notification.record-messages=true")
class SelfServiceEmailChangeContractIntegrationTest extends SelfServiceContractTestBase {

    /** Os dois links de um pedido aceito, lidos logo depois do pedido. */
    private record Links(String currentAddress, String newAddress) {
    }

    private Links requestAccepted(Account account, String newEmail) throws Exception {
        MvcResult accepted = requestEmailChange(account, newEmail, PASSWORD);
        assertThat(statusOf(accepted)).isEqualTo(202);
        return new Links(recordedMessages.lastTokenFor(account.email()),
                recordedMessages.lastTokenFor(newEmail));
    }

    @Test
    @DisplayName("sem envio de e-mail disponivel a troca nem comeca")
    void withoutEmailDeliveryTheChangeDoesNotStart() throws Exception {
        Account account = confirmedAccount();
        String newEmail = uniqueEmail();
        when(emailTransport.isEmailChangeAvailable()).thenReturn(false);

        MvcResult refused = requestEmailChange(account, newEmail, PASSWORD);

        assertThat(statusOf(refused)).isEqualTo(503);
        assertThat(codeOf(refused)).isEqualTo("email-delivery-unavailable");
        assertThat(recordedMessages.lastTokenFor(newEmail)).isNull();
    }

    @Test
    @DisplayName("o pedido manda um link ao endereco atual e outro ao novo, e fica pendente")
    void requestSendsOneLinkToEachAddress() throws Exception {
        Account account = confirmedAccount();
        String newEmail = uniqueEmail();

        MvcResult accepted = requestEmailChange(account, newEmail, PASSWORD);

        assertThat(statusOf(accepted)).isEqualTo(202);
        assertThat(accepted.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode pending = bodyOf(accepted);
        assertThat(pending.get("newEmail").asString()).isEqualTo(newEmail);
        assertThat(pending.get("currentEmailConfirmed").asBoolean()).isFalse();
        assertThat(pending.get("newEmailConfirmed").asBoolean()).isFalse();
        assertThat(absent(pending.get("expiresAt"))).isFalse();
        String currentLink = recordedMessages.lastTokenFor(account.email());
        String newLink = recordedMessages.lastTokenFor(newEmail);
        assertThat(currentLink).isNotNull();
        assertThat(newLink).isNotNull().isNotEqualTo(currentLink);
        assertThat(profileOf(account).get("pendingEmailChange").get("newEmail").asString())
                .isEqualTo(newEmail);
        assertThat(profileOf(account).get("email").asString())
                .as("o e-mail ainda nao mudou").isEqualTo(account.email());
    }

    @Test
    @DisplayName("o e-mail so muda depois de confirmar os dois enderecos")
    void emailChangesOnlyAfterBothConfirmations() throws Exception {
        Account account = confirmedAccount();
        String newEmail = uniqueEmail();
        Links links = requestAccepted(account, newEmail);

        MvcResult first = confirmEmailChange(links.currentAddress());

        assertThat(statusOf(first)).isEqualTo(200);
        assertThat(bodyOf(first).get("status").asString())
                .isEqualTo("awaiting-other-confirmation");
        assertThat(profileOf(account).get("pendingEmailChange")
                .get("currentEmailConfirmed").asBoolean()).isTrue();
        assertThat(loginStatus(account.email(), PASSWORD))
                .as("com uma confirmacao so, o endereco antigo continua valendo").isEqualTo(200);

        MvcResult second = confirmEmailChange(links.newAddress());

        assertThat(statusOf(second)).isEqualTo(200);
        assertThat(bodyOf(second).get("status").asString()).isEqualTo("completed");
        assertThat(loginStatus(newEmail, PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(account.email(), PASSWORD)).isEqualTo(401);
        JsonNode profile = profileOf(account);
        assertThat(profile.get("email").asString()).isEqualTo(newEmail);
        assertThat(absent(profile.get("pendingEmailChange"))).isTrue();
        assertThat(sessionStateOf(account).get("credentialVersion").asLong())
                .as("trocar o e-mail derruba os JWT emitidos antes").isEqualTo(1L);
    }

    @Test
    @DisplayName("a ordem das duas confirmacoes nao importa")
    void confirmationOrderDoesNotMatter() throws Exception {
        Account account = confirmedAccount();
        String newEmail = uniqueEmail();
        Links links = requestAccepted(account, newEmail);

        assertThat(bodyOf(confirmEmailChange(links.newAddress())).get("status").asString())
                .isEqualTo("awaiting-other-confirmation");
        assertThat(bodyOf(confirmEmailChange(links.currentAddress())).get("status").asString())
                .isEqualTo("completed");
        assertThat(profileOf(account).get("email").asString()).isEqualTo(newEmail);
    }

    @Test
    @DisplayName("o link de troca de e-mail serve uma vez so")
    void emailChangeLinkWorksOnlyOnce() throws Exception {
        Account account = confirmedAccount();
        Links links = requestAccepted(account, uniqueEmail());
        confirmEmailChange(links.currentAddress());

        MvcResult again = confirmEmailChange(links.currentAddress());

        assertThat(statusOf(again)).isEqualTo(400);
        assertThat(codeOf(again)).isEqualTo("invalid-email-change-token");
    }

    @Test
    @DisplayName("pedir troca para o proprio e-mail, em qualquer grafia, e conflito")
    void changingToTheSameAddressIsAConflict() throws Exception {
        Account account = confirmedAccount();

        MvcResult refused = requestEmailChange(account,
                account.email().toUpperCase(Locale.ROOT), PASSWORD);

        assertThat(statusOf(refused)).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("email-change-conflict");
    }

    @Test
    @DisplayName("pedir troca para o e-mail de outra conta da aplicacao e recusado")
    void changingToAnotherAccountsAddressIsRefused() throws Exception {
        Account account = confirmedAccount();
        String taken = uniqueEmail();
        registerAccount(taken, "[]");

        MvcResult refused = requestEmailChange(account, taken, PASSWORD);

        assertThat(statusOf(refused)).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("email-already-in-use");
    }

    @Test
    @DisplayName("endereco tomado por outra conta durante a troca impede a conclusao")
    void addressTakenDuringTheChangeBlocksTheCompletion() throws Exception {
        Account account = confirmedAccount();
        String newEmail = uniqueEmail();
        Links links = requestAccepted(account, newEmail);
        registerAccount(newEmail, "[]");

        MvcResult refused = confirmEmailChange(links.currentAddress());

        assertThat(statusOf(refused)).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("email-already-in-use");
        assertThat(statusOf(confirmEmailChange(links.newAddress())))
                .as("a troca foi cancelada, e o outro link morreu junto").isEqualTo(400);
        JsonNode profile = profileOf(account);
        assertThat(profile.get("email").asString()).isEqualTo(account.email());
        assertThat(absent(profile.get("pendingEmailChange"))).isTrue();
    }

    @Test
    @DisplayName("senha atual errada nao comeca a troca")
    void wrongCurrentPasswordDoesNotStartTheChange() throws Exception {
        Account account = confirmedAccount();
        String newEmail = uniqueEmail();

        MvcResult refused = requestEmailChange(account, newEmail, "SenhaErrada123");

        assertThat(statusOf(refused)).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("current-password-incorrect");
        assertThat(recordedMessages.lastTokenFor(newEmail)).isNull();
    }

    @Test
    @DisplayName("o quarto pedido em quinze minutos e recusado")
    void fourthRequestWithinTheWindowIsRefused() throws Exception {
        Account account = confirmedAccount();
        for (int request = 0; request < 3; request++) {
            assertThat(statusOf(requestEmailChange(account, uniqueEmail(), PASSWORD)))
                    .isEqualTo(202);
        }

        MvcResult refused = requestEmailChange(account, uniqueEmail(), PASSWORD);

        assertThat(statusOf(refused)).isEqualTo(429);
        assertThat(codeOf(refused)).isEqualTo("rate-limit-exceeded");
        assertThat(Long.parseLong(refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isBetween(1L, 901L);
    }

    @Test
    @DisplayName("pedido novo invalida os links do pedido anterior")
    void newRequestKillsThePreviousLinks() throws Exception {
        Account account = confirmedAccount();
        Links first = requestAccepted(account, uniqueEmail());
        Links second = requestAccepted(account, uniqueEmail());

        assertThat(statusOf(confirmEmailChange(first.currentAddress()))).isEqualTo(400);
        assertThat(statusOf(confirmEmailChange(first.newAddress()))).isEqualTo(400);
        assertThat(bodyOf(confirmEmailChange(second.currentAddress())).get("status").asString())
                .isEqualTo("awaiting-other-confirmation");
    }

    @Test
    @DisplayName("cancelar a troca invalida os links e tira o pedido do perfil")
    void cancellingKillsTheLinks() throws Exception {
        Account account = confirmedAccount();
        Links links = requestAccepted(account, uniqueEmail());

        MvcResult cancelled = asApplication(delete(pathOf(account, "/email-change")));

        assertThat(statusOf(cancelled)).isEqualTo(204);
        assertThat(statusOf(confirmEmailChange(links.currentAddress()))).isEqualTo(400);
        assertThat(absent(profileOf(account).get("pendingEmailChange"))).isTrue();
    }

    @Test
    @DisplayName("trocar a senha no meio da troca de e-mail invalida os links")
    void passwordChangeDuringTheEmailChangeKillsTheLinks() throws Exception {
        Account account = confirmedAccount();
        Links links = requestAccepted(account, uniqueEmail());

        assertThat(statusOf(changePassword(account, PASSWORD, NEW_PASSWORD))).isEqualTo(204);

        assertThat(statusOf(confirmEmailChange(links.currentAddress()))).isEqualTo(400);
        assertThat(statusOf(confirmEmailChange(links.newAddress()))).isEqualTo(400);
        assertThat(profileOf(account).get("email").asString()).isEqualTo(account.email());
    }

    @Test
    @DisplayName("link de troca de e-mail de uma aplicacao nao e aceito por outra")
    void linkOfOneApplicationIsNotAcceptedByAnother() throws Exception {
        Account account = confirmedAccount();
        Links links = requestAccepted(account, uniqueEmail());

        MvcResult refused = as(anotherApplicationToken(),
                post("/api/self-service/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + links.currentAddress() + "\"}"));

        assertThat(statusOf(refused)).isEqualTo(400);
        assertThat(bodyOf(confirmEmailChange(links.currentAddress())).get("status").asString())
                .as("e o link continua valendo na aplicacao dona")
                .isEqualTo("awaiting-other-confirmation");
    }

    /**
     * O pedido vence em uma hora. Esperar o prazo real seria uma hora de suite, entao o prazo
     * do pedido e recuado direto na tabela; o link em si continua dentro do prazo dele.
     */
    @Test
    @DisplayName("pedido vencido nao conclui a troca")
    void expiredRequestDoesNotComplete() throws Exception {
        Account account = confirmedAccount();
        Links links = requestAccepted(account, uniqueEmail());
        assertThat(database.statement("UPDATE CLIENT_EMAIL_CHANGE"
                        + " SET EXPIRES_AT = SYSTIMESTAMP - INTERVAL '5' MINUTE"
                        + " WHERE CLIENT_ACCOUNT_ID = :id")
                .with("id", account.numericId()).run()).isEqualTo(1);

        MvcResult refused = confirmEmailChange(links.currentAddress());

        assertThat(statusOf(refused)).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("email-change-conflict");
        assertThat(profileOf(account).get("email").asString()).isEqualTo(account.email());
    }
}
