package br.ufpr.tcc.auth.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.notification.RecordedMessages;
import br.ufpr.tcc.auth.shared.database.AdministrativeSessions;
import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova o vinculo entre conta de cliente e empresa, feito pelo administrador.
 *
 * O portal administrativo informa, no cadastro e na alteracao da conta, a lista de empresas
 * que ela representa. Essa lista e o que o login devolve a aplicacao como autorizacao, entao
 * o que esta classe protege e o caminho inteiro: do pedido do administrador ate o CNPJ que a
 * aplicacao recebe quando o cliente entra.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.notification.record-messages=true")
class AccountCompanyLinkContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(300_000L);
    private static final String PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    @Autowired
    private RecordedMessages recordedMessages;

    private String administrativeToken;
    private String applicationToken;
    private String applicationId;

    @BeforeEach
    void seed() {
        recordedMessages.clear();
        administrativeToken = AdministrativeSessions.open(database);
        long id = NEXT.incrementAndGet();
        String code = "vinculo-" + id;
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", id).with("code", code).with("now", OffsetDateTime.now()).run();
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", id)
                .with("digest", token.digest().asBytes()).with("now", OffsetDateTime.now()).run();
        applicationToken = token.clearValue();
        applicationId = Long.toString(id);
    }

    private record Company(String id, String cnpj) {
    }

    private Company createCompany() throws Exception {
        String cnpj = String.format("%014d", NEXT.incrementAndGet());
        MvcResult created = mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa Exemplo\",\"cnpj\":\"" + cnpj + "\"}"))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return new Company(JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asString(), cnpj);
    }

    private String uniqueEmail() {
        return "representante" + NEXT.incrementAndGet() + "@exemplo.com";
    }

    private static String quoted(List<String> identifiers) {
        return identifiers.stream().map(id -> "\"" + id + "\"").toList().toString();
    }

    private MvcResult createAccount(String email, List<String> companyIds) throws Exception {
        return mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"%s","role":"CLIENTE",
                                 "serviceId":"%s","companyIds":%s}"""
                                .formatted(email, PASSWORD, applicationId, quoted(companyIds))))
                .andReturn();
    }

    private MvcResult updateAccount(String accountId, String body) throws Exception {
        return mockMvc.perform(put("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private JsonNode readAccount(String accountId, String token) throws Exception {
        return JSON.readTree(mockMvc.perform(get("/api/users/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> textsOf(JsonNode array) {
        List<String> texts = new ArrayList<>();
        for (JsonNode element : array) {
            texts.add(element.asString());
        }
        return texts;
    }

    private JsonNode loginAfterConfirming(String email) throws Exception {
        mockMvc.perform(post("/api/auth/confirmations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + recordedMessages.lastTokenFor(email) + "\"}"));
        MvcResult signIn = mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        assertThat(signIn.getResponse().getStatus()).isEqualTo(200);
        return JSON.readTree(signIn.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("a conta nasce vinculada as empresas informadas, e o login as devolve")
    void accountIsBornLinkedAndTheLoginAnswersWithTheCompanies() throws Exception {
        Company first = createCompany();
        Company second = createCompany();
        String email = uniqueEmail();

        MvcResult created = createAccount(email, List.of(second.id(), first.id()));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = JSON.readTree(created.getResponse().getContentAsString());
        assertThat(textsOf(body.get("companyIds")))
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(textsOf(body.get("authorizations").get("companyCnpjs")))
                .containsExactlyInAnyOrder(first.cnpj(), second.cnpj());
        assertThat(textsOf(loginAfterConfirming(email).get("companyCnpjs")))
                .as("a aplicacao recebe os CNPJ em ordem, quando o cliente entra")
                .containsExactly(first.cnpj(), second.cnpj());
    }

    @Test
    @DisplayName("a aplicacao le as empresas das proprias contas")
    void applicationReadsTheCompaniesOfItsOwnAccounts() throws Exception {
        Company company = createCompany();
        String accountId = JSON.readTree(createAccount(uniqueEmail(), List.of(company.id()))
                .getResponse().getContentAsString()).get("id").asString();

        JsonNode read = readAccount(accountId, applicationToken);
        JsonNode listing = JSON.readTree(mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn().getResponse().getContentAsString());

        assertThat(textsOf(read.get("companyIds"))).containsExactly(company.id());
        assertThat(textsOf(read.get("authorizations").get("companyCnpjs")))
                .containsExactly(company.cnpj());
        assertThat(listing).hasSize(1);
        assertThat(textsOf(listing.get(0).get("companyIds"))).containsExactly(company.id());
    }

    @Test
    @DisplayName("conta cadastrada sem lista de empresas nasce sem empresa nenhuma")
    void accountWithoutTheListIsBornWithoutCompanies() throws Exception {
        String email = uniqueEmail();

        MvcResult created = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"%s",
                                 "serviceId":"%s"}""".formatted(email, PASSWORD, applicationId)))
                .andReturn();

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(loginAfterConfirming(email).get("companyCnpjs")).isEmpty();
    }

    @Test
    @DisplayName("a mesma empresa repetida no pedido e um vinculo so")
    void repeatedCompanyInTheRequestIsASingleLink() throws Exception {
        Company company = createCompany();

        MvcResult created = createAccount(uniqueEmail(), List.of(company.id(), company.id()));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(textsOf(JSON.readTree(created.getResponse().getContentAsString())
                .get("companyIds"))).containsExactly(company.id());
    }

    @Test
    @DisplayName("empresa inexistente recusa o cadastro inteiro, sem conta e sem mensagem")
    void unknownCompanyRefusesTheWholeRegistration() throws Exception {
        Company existing = createCompany();
        String email = uniqueEmail();

        MvcResult refused = createAccount(email, List.of(existing.id(), "999999999999"));

        assertThat(refused.getResponse().getStatus()).isEqualTo(404);
        assertThat(refused.getResponse().getContentAsString()).contains("Empresa");
        assertThat(database.statement("SELECT COUNT(*) FROM CLIENT_ACCOUNT"
                        + " WHERE EMAIL_NORMALIZED = LOWER(:email)").with("email", email).count())
                .as("a recusa desfaz a conta que ja tinha sido gravada").isZero();
        assertThat(createAccount(email, List.of(existing.id())).getResponse().getStatus())
                .as("o mesmo endereco continua livre").isEqualTo(201);
    }

    @Test
    @DisplayName("identificador de empresa que nao e numero e recusado pela validacao")
    void companyIdentifierThatIsNotANumberIsRefused() throws Exception {
        assertThat(createAccount(uniqueEmail(), List.of("abc")).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("a alteracao substitui a lista inteira de empresas")
    void updateReplacesTheWholeList() throws Exception {
        Company first = createCompany();
        Company second = createCompany();
        Company third = createCompany();
        String email = uniqueEmail();
        String accountId = JSON.readTree(createAccount(email, List.of(first.id(), second.id()))
                .getResponse().getContentAsString()).get("id").asString();

        MvcResult updated = updateAccount(accountId, """
                {"name":"Pessoa Renomeada","active":true,"companyIds":["%s","%s"]}"""
                .formatted(second.id(), third.id()));

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(updated.getResponse().getContentAsString());
        assertThat(body.get("name").asString()).isEqualTo("Pessoa Renomeada");
        assertThat(textsOf(body.get("companyIds")))
                .containsExactlyInAnyOrder(second.id(), third.id());
        assertThat(textsOf(readAccount(accountId, administrativeToken).get("companyIds")))
                .as("o que a alteracao respondeu e o que ficou gravado")
                .containsExactlyInAnyOrder(second.id(), third.id());
        assertThat(textsOf(loginAfterConfirming(email).get("companyCnpjs")))
                .containsExactlyInAnyOrder(second.cnpj(), third.cnpj());
    }

    @Test
    @DisplayName("alteracao sem a lista desfaz todos os vinculos")
    void updateWithoutTheListRemovesEveryLink() throws Exception {
        Company company = createCompany();
        String accountId = JSON.readTree(createAccount(uniqueEmail(), List.of(company.id()))
                .getResponse().getContentAsString()).get("id").asString();

        MvcResult updated = updateAccount(accountId, """
                {"name":"Pessoa","active":true}""");

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(readAccount(accountId, administrativeToken).get("companyIds")).isEmpty();
    }

    /**
     * Os vinculos anteriores sao desfeitos antes de os novos entrarem. Se a recusa nao
     * desfizesse a transacao, um pedido com identificador errado deixaria a conta sem
     * empresa nenhuma, e o cliente perderia a autorizacao por um erro de digitacao alheio.
     */
    @Test
    @DisplayName("empresa inexistente na alteracao nao mexe em nada do que estava gravado")
    void unknownCompanyOnUpdateLeavesTheAccountUntouched() throws Exception {
        Company company = createCompany();
        String accountId = JSON.readTree(createAccount(uniqueEmail(), List.of(company.id()))
                .getResponse().getContentAsString()).get("id").asString();

        MvcResult refused = updateAccount(accountId, """
                {"name":"Nome que nao pode ficar","active":false,"companyIds":["999999999999"]}""");

        assertThat(refused.getResponse().getStatus()).isEqualTo(404);
        JsonNode afterwards = readAccount(accountId, administrativeToken);
        assertThat(textsOf(afterwards.get("companyIds"))).containsExactly(company.id());
        assertThat(afterwards.get("name").asString()).isEqualTo("Pessoa");
        assertThat(afterwards.get("active").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("contas de aplicacoes diferentes representam a mesma empresa")
    void accountsOfDifferentApplicationsRepresentTheSameCompany() throws Exception {
        Company company = createCompany();
        long otherApplication = NEXT.incrementAndGet();
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", otherApplication).with("code", "vinculo-outra-" + otherApplication)
                .with("now", OffsetDateTime.now()).run();

        MvcResult here = createAccount(uniqueEmail(), List.of(company.id()));
        MvcResult there = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"%s","password":"%s",
                                 "serviceId":"%d","companyIds":["%s"]}"""
                                .formatted(uniqueEmail(), PASSWORD, otherApplication,
                                        company.id())))
                .andReturn();

        assertThat(here.getResponse().getStatus()).isEqualTo(201);
        assertThat(there.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("excluir a conta desfaz os vinculos e libera a exclusao da empresa")
    void removingTheAccountRemovesItsLinks() throws Exception {
        Company company = createCompany();
        String accountId = JSON.readTree(createAccount(uniqueEmail(), List.of(company.id()))
                .getResponse().getContentAsString()).get("id").asString();

        int removedAccount = mockMvc.perform(delete("/api/users/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();
        int removedCompany = mockMvc.perform(delete("/api/companies/" + company.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(removedAccount).isEqualTo(204);
        assertThat(removedCompany).isEqualTo(204);
    }
}
