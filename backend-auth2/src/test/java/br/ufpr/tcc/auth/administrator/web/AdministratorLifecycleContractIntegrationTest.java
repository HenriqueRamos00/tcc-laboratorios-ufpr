package br.ufpr.tcc.auth.administrator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova o cadastro de administrador inteiro, que e a parte do contrato publico que faltava:
 * criar, consultar, atualizar e excluir.
 *
 * A classe limpa a tabela de administradores antes de comecar e semeia um unico dono, pelo
 * mesmo motivo da classe de instalacao: dois casos aqui afirmam sobre a quantidade de
 * administradores ativos, e isso nao pode depender do que outra classe de teste deixou para
 * tras. Os casos sao ordenados porque este e um ciclo de vida, e a ordem faz parte do que
 * esta sendo provado.
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdministratorLifecycleContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OWNER_EMAIL = "dono.do.cadastro@lactec.exemplo";
    private static final String OWNER_PASSWORD = "SenhaForte123";
    private static final String INVITED_EMAIL = "convidado@lactec.exemplo";
    private static final String INVITED_PASSWORD = "OutraSenha456";
    private static final String RENAMED_EMAIL = "convidado.renomeado@lactec.exemplo";
    private static final String SECOND_EMAIL = "segundo.do.cadastro@lactec.exemplo";
    private static final String SECOND_PASSWORD = "TerceiraSenha789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String ownerIdentifier;
    private String ownerToken;
    private String invitedIdentifier;
    private String secondIdentifier;
    private String secondToken;

    @BeforeAll
    void seedTheSingleOwner() throws Exception {
        jdbcClient.sql("DELETE FROM ADMINISTRATIVE_SESSION").update();
        jdbcClient.sql("DELETE FROM ADMINISTRATOR").update();
        jdbcClient.sql("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Dono do cadastro', :email, :email, TRUE, :hash, :now,"
                        + " :now)")
                .param("id", 900_001L).param("email", OWNER_EMAIL)
                .param("hash", passwordEncoder.encode(OWNER_PASSWORD))
                .param("now", OffsetDateTime.now()).update();
        ownerIdentifier = "900001";
        ownerToken = signIn(OWNER_EMAIL, OWNER_PASSWORD);
    }

    @Test
    @Order(1)
    @DisplayName("administrador cadastrado pela rota consegue entrar com a senha que recebeu")
    void registeredAdministratorCanSignIn() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Convidado","email":"%s","password":"%s"}"""
                                .formatted(INVITED_EMAIL, INVITED_PASSWORD)))
                .andReturn();

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        invitedIdentifier = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asString();
        assertThat(signIn(INVITED_EMAIL, INVITED_PASSWORD)).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("senha sem digito e recusada no cadastro de administrador")
    void passwordWithoutDigitIsRefused() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sem digito","email":"sem.digito@lactec.exemplo",
                                 "password":"SenhaSemNumero"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /**
     * 409 aqui e aceitavel porque quem pergunta ja tem direito de ler a lista inteira de
     * administradores, entao a resposta nao conta nada que ele nao possa consultar.
     */
    @Test
    @Order(3)
    @DisplayName("e-mail ja cadastrado responde conflito, mesmo escrito em outra caixa")
    void emailAlreadyRegisteredAnswersConflict() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Repetido","email":"CONVIDADO@LACTEC.EXEMPLO",
                                 "password":"TerceiraSenha789"}"""))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @Order(4)
    @DisplayName("a consulta devolve o administrador sem nenhum campo de credencial")
    void readCarriesNoCredentialField() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admins/" + invitedIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        JsonNode administrator = JSON.readTree(body);
        assertThat(administrator.get("id").asString()).isEqualTo(invitedIdentifier);
        assertThat(administrator.get("email").asString()).isEqualTo(INVITED_EMAIL);
        assertThat(administrator.get("active").asBoolean()).isTrue();
        assertThat(body).doesNotContain("password").doesNotContain("hash");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .as("cadastro de administrador nao fica guardado em cache")
                .contains("no-store");
    }

    @Test
    @Order(5)
    @DisplayName("identificador inventado e identificador que nao e numero respondem 404")
    void madeUpIdentifierAnswersNotFound() throws Exception {
        MvcResult numeric = mockMvc.perform(get("/api/admins/987654321")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();
        MvcResult textual = mockMvc.perform(get("/api/admins/nao-e-numero")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();

        assertThat(numeric.getResponse().getStatus()).isEqualTo(404);
        assertThat(textual.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @Order(6)
    @DisplayName("a atualizacao troca o endereco que entra, e o anterior deixa de entrar")
    void updateChangesTheAddressThatSignsIn() throws Exception {
        MvcResult updated = mockMvc.perform(put("/api/admins/" + invitedIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Convidado renomeado","email":"%s","active":true}"""
                                .formatted(RENAMED_EMAIL)))
                .andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(signIn(RENAMED_EMAIL, INVITED_PASSWORD)).isNotBlank();
        assertThat(statusOfSignIn(INVITED_EMAIL, INVITED_PASSWORD)).isEqualTo(401);
    }

    /**
     * Troca de senha existe para o caso de a conta estar comprometida, entao deixar a
     * sessao anterior de pe devolveria o acesso a quem a trocar deveria ter tirado.
     */
    @Test
    @Order(7)
    @DisplayName("trocar a senha encerra as sessoes que o administrador tinha abertas")
    void passwordChangeClosesTheOpenSessions() throws Exception {
        String invitedToken = signIn(RENAMED_EMAIL, INVITED_PASSWORD);
        assertThat(statusOfListingWith(invitedToken)).isEqualTo(200);

        MvcResult updated = mockMvc.perform(put("/api/admins/" + invitedIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Convidado renomeado","email":"%s","active":true,
                                 "password":"SenhaTrocada321"}"""
                                .formatted(RENAMED_EMAIL)))
                .andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOfListingWith(invitedToken)).isEqualTo(401);
        assertThat(signIn(RENAMED_EMAIL, "SenhaTrocada321")).isNotBlank();
    }

    /**
     * Desativar o unico administrador ativo tranca todo mundo fora para sempre: a rota de
     * instalacao continua fechada, porque ela conta linha na tabela e a linha continua la.
     */
    @Test
    @Order(8)
    @DisplayName("desativar o unico administrador ativo e recusado")
    void deactivatingTheOnlyActiveAdministratorIsRefused() throws Exception {
        jdbcClient.sql("UPDATE ADMINISTRATOR SET ACTIVE = FALSE WHERE ID <> :owner")
                .param("owner", Long.parseLong(ownerIdentifier)).update();

        MvcResult refused = mockMvc.perform(put("/api/admins/" + ownerIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dono do cadastro","email":"%s","active":false}"""
                                .formatted(OWNER_EMAIL)))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(statusOfListingWith(ownerToken)).isEqualTo(200);
    }

    @Test
    @Order(9)
    @DisplayName("excluir o unico administrador ativo e recusado pelo mesmo motivo")
    void deletingTheOnlyActiveAdministratorIsRefused() throws Exception {
        MvcResult refused = mockMvc.perform(delete("/api/admins/" + ownerIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(statusOfListingWith(ownerToken)).isEqualTo(200);
    }

    @Test
    @Order(10)
    @DisplayName("excluir administrador desativado funciona e apaga as sessoes dele")
    void deleteRemovesTheAdministratorAndItsSessions() throws Exception {
        MvcResult removed = mockMvc.perform(delete("/api/admins/" + invitedIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();

        assertThat(removed.getResponse().getStatus()).isEqualTo(204);
        assertThat(statusOfReadingOf(invitedIdentifier)).isEqualTo(404);
        Long remainingSessions = jdbcClient
                .sql("SELECT COUNT(*) FROM ADMINISTRATIVE_SESSION WHERE ADMINISTRATOR_ID = :id")
                .param("id", Long.parseLong(invitedIdentifier)).query(Long.class).single();
        assertThat(remainingSessions).isZero();
    }

    @Test
    @Order(11)
    @DisplayName("credencial de aplicacao nao administra administrador, em nenhum verbo")
    void applicationCredentialCannotManageAdministrators() throws Exception {
        String applicationToken = issueApplicationToken();

        assertThat(statusOf(post("/api/admins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Intruso","email":"intruso@lactec.exemplo",
                         "password":"SenhaForte123"}"""))).isEqualTo(403);
        assertThat(statusOf(put("/api/admins/" + ownerIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Intruso","email":"intruso@lactec.exemplo","active":false}""")))
                .isEqualTo(403);
        assertThat(statusOf(delete("/api/admins/" + ownerIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))).isEqualTo(403);
        assertThat(statusOf(get("/api/admins/" + ownerIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))).isEqualTo(403);
    }

    @Test
    @Order(12)
    @DisplayName("senha sem maiuscula e recusada na atualizacao, e a atual continua valendo")
    void weakPasswordIsRefusedOnUpdate() throws Exception {
        MvcResult refused = mockMvc.perform(put("/api/admins/" + ownerIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dono do cadastro","email":"%s","active":true,
                                 "password":"senhafraca123"}""".formatted(OWNER_EMAIL)))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(statusOfSignIn(OWNER_EMAIL, OWNER_PASSWORD))
                .as("a senha anterior continua valendo").isEqualTo(200);
    }

    /**
     * Os quatro desfechos de identificador que nao existe saem iguais, cabecalho incluido.
     * 404 esta entre os status que um cache pode guardar por heuristica, e este 404 e a mesma
     * resposta que recurso de outra aplicacao recebe.
     */
    @Test
    @Order(13)
    @DisplayName("identificador inventado responde 404 sem corpo e sem cache em todo verbo")
    void madeUpIdentifierAnswersNotFoundOnEveryVerb() throws Exception {
        MvcResult read = mockMvc.perform(get("/api/admins/987654321")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();
        MvcResult updated = mockMvc.perform(put("/api/admins/987654321")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Inventado","email":"inventado@lactec.exemplo",
                                 "active":true}"""))
                .andReturn();
        MvcResult removed = mockMvc.perform(delete("/api/admins/987654321")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();
        MvcResult textual = mockMvc.perform(delete("/api/admins/nao-e-numero")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn();

        for (MvcResult result : List.of(read, updated, removed, textual)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(result.getResponse().getContentAsString()).isEmpty();
            assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                    .contains("no-store");
        }
    }

    @Test
    @Order(14)
    @DisplayName("com dois ativos, desativar um e permitido e corta o acesso dele na hora")
    void deactivatingOneOfTwoActiveAdministratorsIsAllowed() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo","email":"%s","password":"%s"}"""
                                .formatted(SECOND_EMAIL, SECOND_PASSWORD)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        secondIdentifier = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asString();
        secondToken = signIn(SECOND_EMAIL, SECOND_PASSWORD);
        assertThat(statusOfListingWith(secondToken)).isEqualTo(200);

        MvcResult deactivated = mockMvc.perform(put("/api/admins/" + secondIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo","email":"%s","active":false}"""
                                .formatted(SECOND_EMAIL)))
                .andReturn();

        assertThat(deactivated.getResponse().getStatus())
                .as("com outro ativo, desativar e permitido").isEqualTo(200);
        assertThat(statusOfListingWith(secondToken))
                .as("desativado deixa de ser atendido na hora").isEqualTo(401);
        assertThat(statusOfListingWith(ownerToken)).isEqualTo(200);
    }

    /**
     * Desativar precisa encerrar a sessao, e nao so deixar de resolver o token enquanto a
     * situacao estiver desligada: a consulta de sessao exige administrador ativo, entao
     * reativar devolveria o acesso a quem estava com o token no dia do afastamento, que e
     * justamente o motivo pelo qual se desativa alguem.
     */
    @Test
    @Order(15)
    @DisplayName("reativar nao ressuscita o token que o administrador tinha antes")
    void reactivatingDoesNotRevivePreviousTokens() throws Exception {
        MvcResult reactivated = mockMvc.perform(put("/api/admins/" + secondIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo","email":"%s","active":true}"""
                                .formatted(SECOND_EMAIL)))
                .andReturn();

        assertThat(reactivated.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOfListingWith(secondToken))
                .as("a sessao aberta antes da desativacao continua encerrada").isEqualTo(401);
        assertThat(statusOfListingWith(signIn(SECOND_EMAIL, SECOND_PASSWORD))).isEqualTo(200);
    }

    @Test
    @Order(16)
    @DisplayName("endereco de outro administrador e recusado na atualizacao, em qualquer caixa")
    void emailOfAnotherAdministratorIsRefusedOnUpdate() throws Exception {
        MvcResult refused = mockMvc.perform(put("/api/admins/" + secondIdentifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo","email":"%s","active":true}"""
                                .formatted(OWNER_EMAIL.toUpperCase(Locale.ROOT))))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(statusOfSignIn(OWNER_EMAIL, OWNER_PASSWORD))
                .as("o endereco do dono continua sendo dele").isEqualTo(200);
        assertThat(statusOfSignIn(SECOND_EMAIL, SECOND_PASSWORD)).isEqualTo(200);
    }

    /**
     * A situacao e lida no instante da chamada, e nao no login. O caso desativa por fora da
     * rota de proposito: e ele que exerce a exigencia de administrador ativo na consulta de
     * sessao, que a revogacao feita pela rota de atualizacao esconderia.
     */
    @Test
    @Order(17)
    @DisplayName("administrador desativado por fora da rota deixa de ser reconhecido")
    void administratorDeactivatedOutsideTheRouteStopsBeingRecognized() throws Exception {
        String freshToken = signIn(SECOND_EMAIL, SECOND_PASSWORD);
        assertThat(statusOfListingWith(freshToken)).isEqualTo(200);

        jdbcClient.sql("UPDATE ADMINISTRATOR SET ACTIVE = FALSE WHERE ID = :id")
                .param("id", Long.parseLong(secondIdentifier)).update();

        MvcResult afterwards = mockMvc.perform(get("/api/admins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken))
                .andReturn();

        assertThat(afterwards.getResponse().getStatus()).isEqualTo(401);
        assertThat(afterwards.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .as("credencial de chamador recusada leva desafio").startsWith("Bearer");
    }

    /**
     * O endereco normalizado e calculado em Java e conferido por uma restricao que usa a
     * funcao do banco. Divergencia entre as duas viraria erro de servidor onde o contrato
     * promete cadastro ou recusa, e o candidato conhecido e o I maiusculo com ponto, que em
     * Java desce para duas unidades de codigo.
     */
    @Test
    @Order(18)
    @DisplayName("endereco com I maiusculo com ponto nao pode virar erro de servidor")
    void emailWithDottedCapitalLetterDoesNotBreakTheCheckConstraint() throws Exception {
        int status = statusOf(post("/api/admins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Pessoa","email":"pessoa@\u0130stanbul.exemplo",
                         "password":"SenhaForte123"}"""));

        assertThat(status).as("ou cadastra, ou recusa a entrada, mas nao estoura")
                .isIn(201, 400);
    }

    private String issueApplicationToken() throws Exception {
        MvcResult application = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aplicacao do ciclo de administrador",
                                 "code":"ciclo-administrador"}"""))
                .andReturn();
        String applicationIdentifier = JSON
                .readTree(application.getResponse().getContentAsString()).get("id").asString();
        MvcResult issued = mockMvc
                .perform(post("/api/services/" + applicationIdentifier + "/tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Token do ciclo de administrador"}"""))
                .andReturn();
        return JSON.readTree(issued.getResponse().getContentAsString()).get("accessToken")
                .asString();
    }

    private String signIn(String email, String password) throws Exception {
        MvcResult result = performSignIn(email, password);
        return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken")
                .asString();
    }

    private int statusOfSignIn(String email, String password) throws Exception {
        return performSignIn(email, password).getResponse().getStatus();
    }

    private MvcResult performSignIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andReturn();
    }

    private int statusOfListingWith(String token) throws Exception {
        return statusOf(get("/api/admins").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int statusOfReadingOf(String identifier) throws Exception {
        return statusOf(get("/api/admins/" + identifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken));
    }

    private int statusOf(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }
}