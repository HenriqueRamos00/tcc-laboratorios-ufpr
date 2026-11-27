package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Prova o perfil do autoatendimento e o estado da sessao.
 *
 * O perfil e o que o Portal do Cliente mostra na tela de conta, e so existe para conta ativa
 * e confirmada da propria aplicacao. O estado da sessao e o que a API do portal consulta a
 * cada requisicao para derrubar o JWT de quem foi desativado ou trocou a credencial, e por
 * isso ele responde tambem para conta que perdeu o acesso: e justamente ela que precisa ser
 * reconhecida.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.notification.record-messages=true")
class SelfServiceProfileContractIntegrationTest extends SelfServiceContractTestBase {

    @Test
    @DisplayName("o perfil da propria conta traz os dados, a politica de senha e nao vai para cache")
    void profileOfTheOwnAccount() throws Exception {
        Account account = confirmedAccount();

        MvcResult profile = asApplication(get(pathOf(account, "/profile")));

        assertThat(statusOf(profile)).isEqualTo(200);
        assertThat(profile.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode body = bodyOf(profile);
        assertThat(body.get("name").asString()).isEqualTo("Pessoa");
        assertThat(body.get("email").asString()).isEqualTo(account.email());
        assertThat(body.get("role").asString()).isEqualTo("CLIENTE");
        assertThat(body.get("emailConfirmed").asBoolean()).isTrue();
        assertThat(textsOf(body.get("editableFields"))).containsExactly("name");
        assertThat(body.get("passwordPolicy").get("minCodePoints").asLong())
                .as("o autoatendimento exige doze, e nao os oito do cadastro").isEqualTo(12L);
        assertThat(absent(body.get("pendingEmailChange"))).isTrue();
    }

    @Test
    @DisplayName("a troca de e-mail so e oferecida quando existe envio de e-mail")
    void emailChangeIsOfferedOnlyWithEmailDelivery() throws Exception {
        Account account = confirmedAccount();

        assertThat(textsOf(profileOf(account).get("actions")))
                .containsExactly("change-email", "change-password");

        when(emailTransport.isEmailChangeAvailable()).thenReturn(false);
        assertThat(textsOf(profileOf(account).get("actions")))
                .containsExactly("change-password");
    }

    @Test
    @DisplayName("o perfil traz as empresas que a conta representa, com identificador e CNPJ")
    void profileListsTheRepresentedCompanies() throws Exception {
        String cnpj = String.format("%014d", Long.parseLong(applicationId));
        MvcResult company = asAdministrator(post("/api/companies"),
                "{\"name\":\"Empresa Exemplo\",\"cnpj\":\"" + cnpj + "\"}");
        assertThat(statusOf(company)).isEqualTo(201);
        String companyId = bodyOf(company).get("id").asString();
        String email = uniqueEmail();
        Account account = new Account(registerAccount(email, "[\"" + companyId + "\"]"), email);
        asApplication(post("/api/auth/confirmations"),
                "{\"token\":\"" + recordedMessages.lastTokenFor(email) + "\"}");

        JsonNode companies = profileOf(account).get("companies");

        assertThat(companies.size()).isEqualTo(1);
        assertThat(companies.get(0).get("id").asString()).isEqualTo(companyId);
        assertThat(companies.get(0).get("cnpj").asString()).isEqualTo(cnpj);
    }

    @Test
    @DisplayName("conta de outra aplicacao responde como inexistente")
    void accountOfAnotherApplicationAnswersAsMissing() throws Exception {
        Account account = confirmedAccount();
        String otherApplication = anotherApplicationToken();

        MvcResult profile = as(otherApplication, get(pathOf(account, "/profile")));
        MvcResult state = as(otherApplication, get(pathOf(account, "/session-state")));

        assertThat(statusOf(profile)).isEqualTo(404);
        assertThat(profile.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(statusOf(state)).isEqualTo(404);
    }

    @Test
    @DisplayName("conta sem e-mail confirmado nao tem perfil, mas tem estado de sessao")
    void unconfirmedAccountHasSessionStateButNoProfile() throws Exception {
        Account account = unconfirmedAccount();

        assertThat(statusOf(asApplication(get(pathOf(account, "/profile"))))).isEqualTo(404);
        JsonNode state = sessionStateOf(account);
        assertThat(state.get("id").asString()).isEqualTo(account.id());
        assertThat(state.get("active").asBoolean()).isTrue();
        assertThat(state.get("emailConfirmed").asBoolean()).isFalse();
        assertThat(state.get("credentialVersion").asLong()).isZero();
    }

    @Test
    @DisplayName("desativar a conta aparece no estado da sessao e tira o perfil")
    void deactivationShowsInTheSessionStateAndRemovesTheProfile() throws Exception {
        Account account = confirmedAccount();
        assertThat(sessionStateOf(account).get("active").asBoolean()).isTrue();

        MvcResult deactivated = asAdministrator(put("/api/users/" + account.id()),
                "{\"name\":\"Pessoa\",\"active\":false}");

        assertThat(statusOf(deactivated)).isEqualTo(200);
        assertThat(sessionStateOf(account).get("active").asBoolean())
                .as("o portal derruba a sessao por este campo").isFalse();
        assertThat(statusOf(asApplication(get(pathOf(account, "/profile"))))).isEqualTo(404);
    }

    @Test
    @DisplayName("alterar o perfil troca so o nome, sem os espacos das pontas")
    void profileChangeRenamesTheAccount() throws Exception {
        Account account = confirmedAccount();

        MvcResult changed = asApplication(patch(pathOf(account, "/profile")),
                "{\"name\":\"  Nome Novo  \"}");

        assertThat(statusOf(changed)).isEqualTo(200);
        assertThat(bodyOf(changed).get("name").asString()).isEqualTo("Nome Novo");
        assertThat(profileOf(account).get("name").asString()).isEqualTo("Nome Novo");
        assertThat(profileOf(account).get("email").asString()).isEqualTo(account.email());
    }

    @Test
    @DisplayName("campo que o perfil nao deixa alterar e recusado, e nada muda")
    void fieldThatTheProfileDoesNotChangeIsRefused() throws Exception {
        Account account = confirmedAccount();

        MvcResult refused = asApplication(patch(pathOf(account, "/profile")),
                "{\"name\":\"Nome Novo\",\"email\":\"outro@exemplo.com\"}");

        assertThat(statusOf(refused)).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("invalid-profile");
        JsonNode profile = profileOf(account);
        assertThat(profile.get("name").asString()).isEqualTo("Pessoa");
        assertThat(profile.get("email").asString()).isEqualTo(account.email());
    }

    private static List<String> textsOf(JsonNode array) {
        List<String> texts = new ArrayList<>();
        for (JsonNode element : array) {
            texts.add(element.asString());
        }
        return texts;
    }
}
