package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.ufpr.tcc.auth.notification.RecordedMessages;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import br.ufpr.tcc.auth.credential.model.ServiceToken;

/**
 * Prova o ciclo de vida de uma conta de cliente, do cadastro ate conseguir entrar.
 *
 * O ponto que esta fatia fecha e simples de enunciar e facil de esquecer: conta criada
 * nasce sem poder entrar, e so o link enviado por e-mail a libera. Sem isso, cadastrar
 * seria cadastrar uma conta inutil.
 */
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(
        properties = "auth.notification.record-messages=true")
class AccountLifecycleContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT_ID = new AtomicLong(600_000L);
    private static final String GOOD_PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RecordedMessages recordedMessages;

    private String applicationToken;
    private String applicationId;

    @BeforeEach
    void registerApplication() {
        recordedMessages.clear();
        long id = NEXT_ID.incrementAndGet();
        String code = "ciclo-" + id;
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", id).param("code", code).param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT_ID.incrementAndGet()).param("applicationId", id)
                .param("digest", token.digest().asBytes()).param("now", OffsetDateTime.now()).update();
        applicationToken = token.clearValue();
        applicationId = Long.toString(id);
    }

    private MvcResult createAccount(String email) throws Exception {
        return mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa de Teste","email":"%s","password":"%s",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(email, GOOD_PASSWORD, applicationId)))
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private MvcResult confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andReturn();
    }

    private String uniqueEmail() {
        return "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
    }

    @Test
    @DisplayName("conta criada nasce sem poder entrar, e o link enviado a libera")
    void createdAccountCannotSignInUntilTheLinkIsUsed() throws Exception {
        String email = uniqueEmail();

        assertThat(createAccount(email).getResponse().getStatus()).isEqualTo(201);
        assertThat(login(email, GOOD_PASSWORD).getResponse().getStatus())
                .as("antes de confirmar").isEqualTo(401);

        String link = recordedMessages.lastTokenFor(email);
        assertThat(confirm(link).getResponse().getStatus()).isEqualTo(204);

        assertThat(login(email, GOOD_PASSWORD).getResponse().getStatus())
                .as("depois de confirmar").isEqualTo(200);
    }

    @Test
    @DisplayName("o link de confirmacao serve uma vez so")
    void confirmationLinkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail();
        createAccount(email);
        String link = recordedMessages.lastTokenFor(email);

        assertThat(confirm(link).getResponse().getStatus()).isEqualTo(204);
        assertThat(confirm(link).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("link desconhecido e link usado respondem igual")
    void unknownAndUsedLinksAnswerAlike() throws Exception {
        String email = uniqueEmail();
        createAccount(email);
        String link = recordedMessages.lastTokenFor(email);
        confirm(link);

        MvcResult used = confirm(link);
        MvcResult neverExisted = confirm("cnf_naoexistiuemlugarnenhumaaaaaaaaaaaaaaaaaaa");

        assertThat(used.getResponse().getStatus()).isEqualTo(neverExisted.getResponse().getStatus());
        assertThat(used.getResponse().getContentAsString())
                .isEqualTo(neverExisted.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("senha fora da politica recusa o cadastro")
    void passwordOutsideThePolicyRejectsTheRegistration() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Fraca","email":"%s","password":"senhafraca",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(uniqueEmail(), applicationId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /**
     * A coluna normalizada e conferida por uma restricao que usa a funcao do banco, e a rota
     * de cadastro de cliente e a mais exposta que grava nela.
     *
     * Medido na rota de administrador antes da correcao: com a minuscula calculada em Java, o
     * I maiusculo com ponto fazia a restricao reprovar e a resposta virava erro de servidor,
     * onde o contrato promete cadastro ou recusa de entrada.
     */
    @Test
    @DisplayName("endereco com I maiusculo com ponto nao vira erro de servidor no cadastro")
    void emailWithDottedCapitalLetterDoesNotBreakTheCheckConstraint() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"pessoa@\u0130stanbul.exemplo",
                                 "password":"SenhaForte123","role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(applicationId)))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("ou cadastra, ou recusa a entrada, mas nao estoura").isIn(201, 400);
    }

    /**
     * Campo que a rota nao honra nao pode ser aceito em silencio.
     *
     * Medido antes da correcao: o pedido de alteracao declarava endereco, perfil e aplicacao,
     * validava os tres, e gravava apenas nome e situacao. Quem mandasse endereco novo recebia
     * 200 com o endereco antigo, e a regra do modulo diz o contrario: ignorar ensina ao
     * chamador que o campo existe e e aceito.
     */
    @Test
    @DisplayName("campo que a alteracao de conta nao honra e recusado, e nao ignorado")
    void fieldThatTheUpdateDoesNotHonourIsRefused() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(email, applicationId)))
                .andReturn();
        String accountId = JSON.readTree(mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asString();

        MvcResult withForeignField = mockMvc.perform(put("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nome novo","active":true,
                                 "email":"outro.endereco@lactec.exemplo"}"""))
                .andReturn();

        assertThat(withForeignField.getResponse().getStatus()).isEqualTo(400);
    }

    /**
     * A situacao da conta decide se ela entra, entao ela nao pode mudar por omissao de campo.
     * Medido antes da correcao: campo ausente valia verdadeiro, e um pedido de renomeacao
     * reativava conta desativada em silencio.
     */
    @Test
    @DisplayName("alterar conta sem declarar a situacao e recusado, e nao reativa em silencio")
    void updateWithoutTheSituationIsRefused() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"SenhaForte123",
                                 "role":"CLIENTE","serviceId":"%s"}"""
                                .formatted(email, applicationId)))
                .andReturn();
        String accountId = JSON.readTree(mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asString();

        mockMvc.perform(put("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","active":false}"""))
                .andReturn();

        MvcResult withoutSituation = mockMvc.perform(put("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nome novo","email":"%s","role":"CLIENTE",
                                 "serviceId":"%s"}""".formatted(email, applicationId)))
                .andReturn();

        assertThat(withoutSituation.getResponse().getStatus()).isEqualTo(400);
        MvcResult afterwards = mockMvc.perform(get("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn();
        assertThat(JSON.readTree(afterwards.getResponse().getContentAsString())
                .get("active").asBoolean())
                .as("a conta continua desativada").isFalse();
    }

    // A aplicacao que chama so cadastra dentro dela mesma. O identificador que vem no
    // corpo e pedido, e pedido que contradiz a credencial e recusado em vez de
    // ignorado em silencio, porque ignorar ensina que o campo existe.
    @Test
    @DisplayName("aplicacao nao cadastra conta em outra aplicacao")
    void applicationCannotRegisterIntoAnotherApplication() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invasora","email":"%s","password":"%s",
                                 "role":"CLIENTE","serviceId":"999999999"}"""
                                .formatted(uniqueEmail(), GOOD_PASSWORD)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("e-mail repetido na mesma aplicacao responde 409")
    void repeatedEmailInTheSameApplicationAnswersConflict() throws Exception {
        String email = uniqueEmail();
        createAccount(email);

        assertThat(createAccount(email).getResponse().getStatus()).isEqualTo(409);
    }
}
