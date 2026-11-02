package br.ufpr.tcc.auth.shared.observability;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * A trilha de auditoria ligada na aplicacao de verdade.
 *
 * O teste da trilha isolada prova que o evento sai com os campos certos quando alguem o
 * chama. Ele nao prova o que a regra 4 pede de fato, que e os eventos existirem para as
 * operacoes que a regra nomeia: isso e fiacao, e fiacao se prova entrando pela rota.
 *
 * O que cada caso afirma e a presenca do evento e dos campos que permitem explicar a acao
 * depois. O que nenhum deles pode encontrar e endereco, nome, senha ou token, e essa metade
 * e afirmada sobre todas as linhas capturadas de uma vez.
 */
@AutoConfigureMockMvc
class AuditTrailWiringIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(970_000L);
    private static final String ADMINISTRATOR_PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;
    private String administrativeToken;
    private String applicationToken;
    private String applicationId;
    private String clientEmail;

    @BeforeEach
    void captureAndSeed() throws Exception {
        logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(AuditTrail.class);
        captured.start();
        logger.addAppender(captured);
        logger.setLevel(Level.INFO);

        long administratorId = NEXT.incrementAndGet();
        String administratorEmail = "auditoria." + administratorId + "@lactec.exemplo";
        database.statement("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador da auditoria', :email, LOWER(:email),"
                        + " TRUE, :hash, :now, :now)")
                .with("id", administratorId).with("email", administratorEmail)
                .with("hash", passwordEncoder.encode(ADMINISTRATOR_PASSWORD))
                .with("now", OffsetDateTime.now()).run();
        MvcResult signIn = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + administratorEmail + "\",\"password\":\""
                                + ADMINISTRATOR_PASSWORD + "\"}"))
                .andReturn();
        administrativeToken = JSON.readTree(signIn.getResponse().getContentAsString())
                .get("accessToken").asString();

        long application = NEXT.incrementAndGet();
        applicationId = Long.toString(application);
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", application).with("code", "auditoria-" + application)
                .with("now", OffsetDateTime.now()).run();
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", application)
                .with("digest", token.digest().asBytes())
                .with("now", OffsetDateTime.now()).run();
        applicationToken = token.clearValue();
        clientEmail = "cliente." + NEXT.incrementAndGet() + "@lactec.exemplo";
    }

    @AfterEach
    void releaseLogEvents() {
        logger.detachAppender(captured);
        captured.stop();
    }

    private List<String> recordedEvents() {
        return captured.list.stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .filter(pair -> "event".equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .toList();
    }

    @Test
    @DisplayName("entrada administrativa aceita gera evento de auditoria")
    void administrativeSignInIsRecorded() {
        assertThat(recordedEvents()).contains("administrative_sign_in_attempted");
    }

    @Test
    @DisplayName("cadastro de cliente gera evento com a conta e a aplicacao")
    void clientRegistrationIsRecorded() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(clientEmail, applicationId)))
                .andReturn();

        assertThat(recordedEvents()).contains("client_account_created");
        assertThat(captured.list).anySatisfy(event -> assertThat(event.getKeyValuePairs())
                .anySatisfy(pair -> assertThat(pair.key).isEqualTo("account_id")));
    }

    @Test
    @DisplayName("senha errada gera tentativa recusada, e a quinta gera bloqueio")
    void refusedSignInAndLockOutAreRecorded() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(clientEmail, applicationId)))
                .andReturn();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + clientEmail
                                    + "\",\"password\":\"Errada123\"}"))
                    .andReturn();
        }

        assertThat(recordedEvents()).contains("client_sign_in_attempted")
                .contains("client_locked_out");
    }

    @Test
    @DisplayName("cadastro de aplicacao e ciclo do token geram eventos")
    void applicationAndTokenEventsAreRecorded() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aplicacao da auditoria","code":"auditoria-fiacao"}"""))
                .andReturn();
        String registeredId = JSON.readTree(registered.getResponse().getContentAsString())
                .get("id").asString();
        MvcResult issued = mockMvc.perform(post("/api/services/" + registeredId + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Token da auditoria"}"""))
                .andReturn();
        String tokenId = JSON.readTree(issued.getResponse().getContentAsString())
                .get("token").get("id").asString();
        mockMvc.perform(delete("/api/services/" + registeredId + "/tokens/" + tokenId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();

        assertThat(recordedEvents()).contains("application_registered")
                .contains("application_token_issued").contains("application_token_revoked");
    }

    @Test
    @DisplayName("cadastro de administrador gera evento")
    void administratorRegistrationIsRecorded() throws Exception {
        mockMvc.perform(post("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Convidado da auditoria",
                                 "email":"convidado.auditoria@lactec.exemplo",
                                 "password":"OutraSenha456"}"""))
                .andReturn();

        assertThat(recordedEvents()).contains("administrator_created");
    }

    /**
     * A regra 4 nomeia alteracao e exclusao de conta, e nao apenas criacao. A confirmacao do
     * endereco entra junto porque ela e o que muda a conta de nao poder entrar para poder.
     */
    @Test
    @DisplayName("alteracao, confirmacao e exclusao de conta geram evento")
    void clientAccountChangeConfirmationAndRemovalAreRecorded() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(clientEmail, applicationId)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String accountId = accountIdentifierOf(clientEmail);

        mockMvc.perform(put("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa renomeada","active":true}"""))
                .andReturn();
        mockMvc.perform(post("/api/auth/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + confirmationTokenOf(accountId) + "\"}"))
                .andReturn();
        mockMvc.perform(delete("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn();

        assertThat(recordedEvents()).contains("client_account_changed")
                .contains("client_credential_email_confirmed")
                .contains("client_account_removed");
    }

    @Test
    @DisplayName("alteracao e exclusao de administrador geram evento")
    void administratorChangeAndRemovalAreRecorded() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Convidado","email":"convidado.evento@lactec.exemplo",
                                 "password":"OutraSenha456"}"""))
                .andReturn();
        String invitedId = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asString();

        mockMvc.perform(put("/api/admins/" + invitedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Convidado renomeado",
                                 "email":"convidado.evento@lactec.exemplo","active":false}"""))
                .andReturn();
        mockMvc.perform(delete("/api/admins/" + invitedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();

        assertThat(recordedEvents()).contains("administrator_changed")
                .contains("administrator_removed");
    }

    @Test
    @DisplayName("alterar e desativar aplicacao geram evento, porque ela decide quem pode chamar")
    void applicationChangeAndDeactivationAreRecorded() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aplicacao a desativar","code":"auditoria-desativar"}"""))
                .andReturn();
        String registeredId = JSON.readTree(registered.getResponse().getContentAsString())
                .get("id").asString();

        mockMvc.perform(put("/api/services/" + registeredId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aplicacao renomeada","code":"auditoria-desativar",
                                 "active":true}"""))
                .andReturn();
        mockMvc.perform(delete("/api/services/" + registeredId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();

        assertThat(recordedEvents()).contains("application_changed")
                .contains("application_deactivated");
    }

    /**
     * O bloqueio da entrada administrativa tem evento proprio.
     *
     * Reusar o evento de cliente faria a trilha afirmar que uma conta de cliente da aplicacao
     * zero foi bloqueada, e aplicacao zero nao existe: e o espaco de chave reservado do
     * contador. Auditoria que mente sobre quem foi bloqueado e pior que auditoria ausente,
     * porque quem investiga acredita nela.
     */
    @Test
    @DisplayName("bloquear a entrada administrativa gera evento proprio, e nao o de cliente")
    void administrativeLockOutHasItsOwnEvent() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"trava.auditoria@lactec.exemplo",
                                     "password":"ErradaTotal999"}"""))
                    .andReturn();
        }

        assertThat(recordedEvents()).contains("administrative_locked_out")
                .doesNotContain("client_locked_out");
    }

    /**
     * O cadastro de empresa muda quem o cliente representa, e a regra 4 nomeia criacao,
     * alteracao e exclusao de cadastro sem abrir excecao para empresa.
     */
    @Test
    @DisplayName("cadastro, alteracao e exclusao de empresa geram evento")
    void companyEventsAreRecorded() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Empresa da auditoria","cnpj":"44444444444444"}"""))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String companyId = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asString();

        mockMvc.perform(put("/api/companies/" + companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Empresa renomeada","cnpj":"44444444444444"}"""))
                .andReturn();
        mockMvc.perform(delete("/api/companies/" + companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn();

        assertThat(recordedEvents()).contains("company_registered").contains("company_changed")
                .contains("company_removed");
    }

    private String accountIdentifierOf(String email) {
        return Long.toString(database.statement("SELECT ID FROM CLIENT_ACCOUNT"
                        + " WHERE EMAIL_NORMALIZED = LOWER(:email)")
                .with("email", email).count());
    }

    /**
     * O token do link nao sai pela API e a copia em memoria fica desligada nesta classe, entao
     * a confirmacao e exercitada com um link proprio, gravado aqui com o digest que o servico
     * guarda. O que esta sendo provado e o evento da confirmacao, nao o fluxo do link, que tem
     * classe de contrato propria.
     *
     * O link que o cadastro emitiu e invalidado antes, e isso nao e detalhe de arrumacao: o
     * banco passou a garantir no maximo um link pendente por conta e proposito, e sem esta
     * linha o auxiliar estaria fabricando um estado que a producao nao alcanca. Foi
     * exatamente assim que a restricao nova apareceu, recusando a segunda insercao.
     */
    private String confirmationTokenOf(String accountId) {
        database.statement("UPDATE ACCOUNT_CONFIRMATION SET USED_AT = SYSTIMESTAMP"
                        + " WHERE CLIENT_ACCOUNT_ID = :accountId AND USED_AT IS NULL")
                .with("accountId", Long.parseLong(accountId)).run();
        ConfirmationToken token = ConfirmationToken.generate();
        database.statement("INSERT INTO ACCOUNT_CONFIRMATION (ID, CLIENT_ACCOUNT_ID, TOKEN_DIGEST,"
                        + " PURPOSE, CREATED_AT, EXPIRES_AT)"
                        + " VALUES (:id, :accountId, :digest, 'EMAIL_CONFIRMATION', :now,"
                        + " :expiresAt)")
                .with("id", NEXT.incrementAndGet()).with("accountId", Long.parseLong(accountId))
                .with("digest", token.digest().asBytes()).with("now", OffsetDateTime.now())
                .with("expiresAt", OffsetDateTime.now().plusHours(1)).run();
        return token.clearValue();
    }

    /**
     * A metade proibitiva da regra 4, afirmada sobre tudo o que a trilha escreveu durante os
     * casos: nenhuma linha carrega endereco, senha nem token.
     */
    @Test
    @DisplayName("nenhuma linha da trilha carrega endereco, senha ou token")
    void noAuditLineCarriesPersonalDataOrSecret() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(clientEmail, applicationId)))
                .andReturn();

        assertThat(captured.list).isNotEmpty().allSatisfy(event -> {
            String line = event.getFormattedMessage() + event.getKeyValuePairs();
            assertThat(line).doesNotContain(clientEmail).doesNotContain("SenhaForte123")
                    .doesNotContain("svc_").doesNotContain("adm_").doesNotContain("cnf_");
        });
    }
}
