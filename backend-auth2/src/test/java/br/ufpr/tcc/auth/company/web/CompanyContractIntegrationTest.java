package br.ufpr.tcc.auth.company.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.AdministrativeSessions;
import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova o cadastro de empresa pelo administrador.
 *
 * A empresa nao pertence a aplicacao nenhuma: quem a cadastra e o portal administrativo, e a
 * mesma pessoa juridica e uma empresa so para o servico inteiro. Por isso o CNPJ e unico no
 * servico, e a credencial de aplicacao nao alcanca estas rotas.
 */
@AutoConfigureMockMvc
class CompanyContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(100_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    private String administrativeToken;
    private String applicationToken;
    private long applicationId;

    @BeforeEach
    void seed() {
        administrativeToken = AdministrativeSessions.open(database);
        applicationId = NEXT.incrementAndGet();
        String code = "empresa-" + applicationId;
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", applicationId).with("code", code).with("now", OffsetDateTime.now())
                .run();
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", applicationId)
                .with("digest", token.digest().asBytes()).with("now", OffsetDateTime.now()).run();
        applicationToken = token.clearValue();
    }

    private MvcResult createCompany(String token, String cnpj) throws Exception {
        return mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa Exemplo\",\"cnpj\":\"" + cnpj + "\"}"))
                .andReturn();
    }

    private String createdCompanyId(String cnpj) throws Exception {
        MvcResult created = createCompany(administrativeToken, cnpj);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return JSON.readTree(created.getResponse().getContentAsString()).get("id").asString();
    }

    private String uniqueCnpj() {
        return String.format("%014d", NEXT.incrementAndGet());
    }

    @Test
    @DisplayName("o administrador cadastra a empresa, e a resposta nao traz aplicacao")
    void administratorRegistersTheCompany() throws Exception {
        String cnpj = uniqueCnpj();

        MvcResult created = createCompany(administrativeToken, cnpj);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = JSON.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("cnpj").asString()).isEqualTo(cnpj);
        assertThat(body.get("id").asString()).matches("[0-9]+");
        assertThat(body.has("serviceId")).as("empresa nao pertence a aplicacao").isFalse();
    }

    @Test
    @DisplayName("credencial de aplicacao nao alcanca o cadastro de empresa")
    void applicationCredentialDoesNotReachTheCompanyRegistry() throws Exception {
        String cnpj = uniqueCnpj();
        String existing = createdCompanyId(uniqueCnpj());

        int created = createCompany(applicationToken, cnpj).getResponse().getStatus();
        int listed = mockMvc.perform(get("/api/companies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn().getResponse().getStatus();
        int read = mockMvc.perform(get("/api/companies/" + existing)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn().getResponse().getStatus();
        int removed = mockMvc.perform(delete("/api/companies/" + existing)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken))
                .andReturn().getResponse().getStatus();

        assertThat(created).isEqualTo(403);
        assertThat(listed).isEqualTo(403);
        assertThat(read).isEqualTo(403);
        assertThat(removed).isEqualTo(403);
        assertThat(database.statement("SELECT COUNT(*) FROM COMPANY WHERE CNPJ = :cnpj")
                .with("cnpj", cnpj).count())
                .as("o 403 e recusa, e nao gravacao seguida de 403").isZero();
        assertThat(database.statement("SELECT COUNT(*) FROM COMPANY WHERE ID = :id")
                .with("id", Long.parseLong(existing)).count()).isOne();
    }

    @Test
    @DisplayName("CNPJ fora do formato e recusado")
    void cnpjOutsideTheFormatIsRejected() throws Exception {
        assertThat(createCompany(administrativeToken, "123").getResponse().getStatus())
                .isEqualTo(400);
        assertThat(createCompany(administrativeToken, "12.345.678/0001-99").getResponse()
                .getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("o mesmo CNPJ nao entra duas vezes no servico")
    void sameCnpjDoesNotFitTwice() throws Exception {
        String cnpj = uniqueCnpj();
        createdCompanyId(cnpj);

        assertThat(createCompany(administrativeToken, cnpj).getResponse().getStatus())
                .isEqualTo(409);
    }

    @Test
    @DisplayName("mover o CNPJ para um que ja existe responde conflito")
    void movingTheCnpjOntoAnExistingOneAnswersAsConflict() throws Exception {
        String occupied = uniqueCnpj();
        createdCompanyId(occupied);
        String identifier = createdCompanyId(uniqueCnpj());

        MvcResult collided = mockMvc.perform(put("/api/companies/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa Exemplo\",\"cnpj\":\"" + occupied + "\"}"))
                .andReturn();

        assertThat(collided.getResponse().getStatus()).isEqualTo(409);
        JsonNode unchanged = JSON.readTree(mockMvc.perform(get("/api/companies/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getContentAsString());
        assertThat(unchanged.get("cnpj").asString())
                .as("o conflito nao grava metade da alteracao").isNotEqualTo(occupied);
    }

    @Test
    @DisplayName("alterar a empresa devolve o que ficou gravado")
    void updatingTheCompanyAnswersWithTheStoredState() throws Exception {
        String identifier = createdCompanyId(uniqueCnpj());
        String newCnpj = uniqueCnpj();

        MvcResult updated = mockMvc.perform(put("/api/companies/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa Renomeada\",\"cnpj\":\"" + newCnpj + "\"}"))
                .andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(updated.getResponse().getContentAsString());
        assertThat(body.get("name").asString()).isEqualTo("Empresa Renomeada");
        assertThat(body.get("cnpj").asString()).isEqualTo(newCnpj);
    }

    @Test
    @DisplayName("a listagem do administrador mostra as empresas do servico")
    void listingShowsTheCompaniesOfTheService() throws Exception {
        String first = uniqueCnpj();
        String second = uniqueCnpj();
        createdCompanyId(first);
        createdCompanyId(second);

        String listing = mockMvc.perform(get("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getContentAsString();

        assertThat(listing).contains(first).contains(second);
    }

    @Test
    @DisplayName("empresa inexistente responde 404 na leitura, na alteracao e na exclusao")
    void unknownCompanyAnswersAsNotFound() throws Exception {
        for (String invented : new String[] {"999999999999", "nao-e-numero"}) {
            int read = mockMvc.perform(get("/api/companies/" + invented)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                    .andReturn().getResponse().getStatus();
            int updated = mockMvc.perform(put("/api/companies/" + invented)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Empresa\",\"cnpj\":\"" + uniqueCnpj() + "\"}"))
                    .andReturn().getResponse().getStatus();
            int removed = mockMvc.perform(delete("/api/companies/" + invented)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                    .andReturn().getResponse().getStatus();

            assertThat(read).as("leitura de %s", invented).isEqualTo(404);
            assertThat(updated).as("alteracao de %s", invented).isEqualTo(404);
            assertThat(removed).as("exclusao de %s", invented).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("empresa sem conta vinculada e removida")
    void companyWithoutLinkedAccountIsRemoved() throws Exception {
        String identifier = createdCompanyId(uniqueCnpj());

        int removed = mockMvc.perform(delete("/api/companies/" + identifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(removed).isEqualTo(204);
        assertThat(mockMvc.perform(get("/api/companies/" + identifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    /**
     * Apagar a empresa tiraria a autorizacao de quem a representa sem ninguem ter pedido
     * isso, entao a exclusao e recusada ate os vinculos serem desfeitos.
     */
    @Test
    @DisplayName("empresa que uma conta ainda representa responde conflito, e nao e removida")
    void companyStillRepresentedByAnAccountIsNotRemoved() throws Exception {
        String identifier = createdCompanyId(uniqueCnpj());
        MvcResult account = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pessoa","email":"representante%d@exemplo.com",
                                 "password":"SenhaForte123","role":"CLIENTE",
                                 "serviceId":"%d","companyIds":["%s"]}"""
                                .formatted(NEXT.incrementAndGet(), applicationId, identifier)))
                .andReturn();
        assertThat(account.getResponse().getStatus()).isEqualTo(201);
        String accountId = JSON.readTree(account.getResponse().getContentAsString())
                .get("id").asString();

        int whileLinked = mockMvc.perform(delete("/api/companies/" + identifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(whileLinked).isEqualTo(409);
        assertThat(mockMvc.perform(get("/api/companies/" + identifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus()).as("a empresa continua la").isEqualTo(200);

        mockMvc.perform(put("/api/users/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Pessoa","active":true,"companyIds":[]}"""));
        int afterUnlinking = mockMvc.perform(delete("/api/companies/" + identifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn().getResponse().getStatus();

        assertThat(afterUnlinking).as("desfeito o vinculo, a exclusao passa").isEqualTo(204);
    }
}
