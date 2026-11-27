package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.notification.EmailTransportAvailability;
import br.ufpr.tcc.auth.notification.RecordedMessages;
import br.ufpr.tcc.auth.shared.database.AdministrativeSessions;
import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Preparacao comum das suites de autoatendimento.
 *
 * O autoatendimento e chamado pela aplicacao, em nome do cliente que ja entrou, e as rotas
 * levam o identificador da conta no caminho. Cada caso parte de uma aplicacao propria, com
 * token proprio, e de contas cadastradas pelo administrador.
 *
 * A troca de e-mail so e oferecida com transporte de e-mail de verdade, e a suite nao tem
 * servidor de e-mail. A disponibilidade e simulada e fica ligada em cada caso, salvo nos que
 * provam o contrario; as mensagens sao lidas de RecordedMessages, como nas suites de link.
 *
 * A faixa de identificadores e propria, longe das que as outras suites reservam.
 */
abstract class SelfServiceContractTestBase extends OracleIntegrationTestBase {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PASSWORD = "SenhaForte123";
    static final String NEW_PASSWORD = "OutraSenha4567";

    private static final AtomicLong NEXT = new AtomicLong(1_200_000L);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DatabaseProbe database;

    @Autowired
    RecordedMessages recordedMessages;

    @MockitoBean
    EmailTransportAvailability emailTransport;

    String administrativeToken;
    String applicationToken;
    String applicationId;

    record Account(String id, String email) {

        long numericId() {
            return Long.parseLong(id);
        }
    }

    @BeforeEach
    void seedApplication() {
        recordedMessages.clear();
        when(emailTransport.isAvailable()).thenReturn(true);
        when(emailTransport.isEmailChangeAvailable()).thenReturn(true);
        administrativeToken = AdministrativeSessions.open(database);
        long id = NEXT.incrementAndGet();
        applicationId = Long.toString(id);
        applicationToken = registerApplication(id);
    }

    /** Aplicacao ativa com um token de integracao; devolve o valor do token. */
    private String registerApplication(long id) {
        OffsetDateTime now = OffsetDateTime.now();
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", id).with("code", "autoatendimento-" + id).with("now", now).run();
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", id)
                .with("digest", token.digest().asBytes()).with("now", now).run();
        return token.clearValue();
    }

    /** Token de outra aplicacao, para provar que uma nao alcanca conta da outra. */
    String anotherApplicationToken() {
        return registerApplication(NEXT.incrementAndGet());
    }

    String uniqueEmail() {
        return "autoatendimento" + NEXT.incrementAndGet() + "@exemplo.com";
    }

    /** Conta ativa e confirmada, que e a unica que o autoatendimento atende. */
    Account confirmedAccount() throws Exception {
        Account account = unconfirmedAccount();
        MvcResult confirmed = asApplication(post("/api/auth/confirmations"),
                "{\"token\":\"" + recordedMessages.lastTokenFor(account.email()) + "\"}");
        assertThat(statusOf(confirmed)).isEqualTo(204);
        return account;
    }

    Account unconfirmedAccount() throws Exception {
        String email = uniqueEmail();
        return new Account(registerAccount(email, "[]"), email);
    }

    /** Cadastra pelo administrador e devolve o identificador da conta. */
    String registerAccount(String email, String companyIdsJson) throws Exception {
        MvcResult created = asAdministrator(post("/api/users"), """
                {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE",
                 "serviceId":"%s","companyIds":%s}"""
                .formatted(email, PASSWORD, applicationId, companyIdsJson));
        assertThat(statusOf(created)).isEqualTo(201);
        return bodyOf(created).get("id").asString();
    }

    static String pathOf(Account account, String suffix) {
        return "/api/self-service/accounts/" + account.id() + suffix;
    }

    MvcResult as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
    }

    MvcResult asApplication(MockHttpServletRequestBuilder request) throws Exception {
        return as(applicationToken, request);
    }

    MvcResult asApplication(MockHttpServletRequestBuilder request, String body) throws Exception {
        return asApplication(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    MvcResult asAdministrator(MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return as(administrativeToken,
                request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    int loginStatus(String email, String password) throws Exception {
        return statusOf(asApplication(post("/api/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    JsonNode profileOf(Account account) throws Exception {
        MvcResult profile = asApplication(get(pathOf(account, "/profile")));
        assertThat(statusOf(profile)).isEqualTo(200);
        return bodyOf(profile);
    }

    JsonNode sessionStateOf(Account account) throws Exception {
        MvcResult state = asApplication(get(pathOf(account, "/session-state")));
        assertThat(statusOf(state)).isEqualTo(200);
        return bodyOf(state);
    }

    MvcResult changePassword(Account account, String currentPassword, String newPassword)
            throws Exception {
        return asApplication(post(pathOf(account, "/password")),
                "{\"currentPassword\":\"" + currentPassword
                        + "\",\"newPassword\":\"" + newPassword + "\"}");
    }

    MvcResult requestEmailChange(Account account, String newEmail, String currentPassword)
            throws Exception {
        return asApplication(post(pathOf(account, "/email-change")),
                "{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"" + currentPassword
                        + "\"}");
    }

    MvcResult confirmEmailChange(String token) throws Exception {
        return asApplication(post("/api/self-service/email-change/confirm"),
                "{\"token\":\"" + token + "\"}");
    }

    static int statusOf(MvcResult result) {
        return result.getResponse().getStatus();
    }

    static JsonNode bodyOf(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    /** O codigo da recusa, no campo que o portal le para escolher a mensagem da tela. */
    static String codeOf(MvcResult result) throws Exception {
        return bodyOf(result).path("code").asString();
    }

    static boolean absent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }
}
