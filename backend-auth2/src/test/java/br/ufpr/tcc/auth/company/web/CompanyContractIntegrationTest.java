package br.ufpr.tcc.auth.company.web;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
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
 * Prova o cadastro de empresa dentro da aplicacao que chama.
 *
 * A empresa pertence a uma aplicacao, e e por isso que o CNPJ e unico dentro dela e nao no
 * servico inteiro: unicidade global faria o cadastro de uma aplicacao recusar um CNPJ por
 * causa de outra, o que conta que aquela empresa ja e cliente do Lactec em outro sistema.
 */
@AutoConfigureMockMvc
class CompanyContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(100_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    private String firstToken;
    private String secondToken;

    private String tenantToken(String code) {
        long id = NEXT.incrementAndGet();
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", id).with("code", code).with("now", OffsetDateTime.now()).run();
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", id)
                .with("digest", token.digest().asBytes()).with("now", OffsetDateTime.now()).run();
        return token.clearValue();
    }

    @BeforeEach
    void registerApplications() {
        firstToken = tenantToken("empresa-a-" + NEXT.incrementAndGet());
        secondToken = tenantToken("empresa-b-" + NEXT.incrementAndGet());
    }

    private MvcResult createCompany(String token, String cnpj) throws Exception {
        return mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa Exemplo\",\"cnpj\":\"" + cnpj + "\"}"))
                .andReturn();
    }

    private String uniqueCnpj() {
        return String.format("%014d", NEXT.incrementAndGet());
    }

    @Test
    @DisplayName("a empresa e cadastrada dentro da aplicacao que chamou")
    void companyIsRegisteredInsideTheCallingApplication() throws Exception {
        String cnpj = uniqueCnpj();

        MvcResult created = createCompany(firstToken, cnpj);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = JSON.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("cnpj").asString()).isEqualTo(cnpj);
        assertThat(body.get("id").asString()).matches("[0-9]+");
    }

    @Test
    @DisplayName("CNPJ fora do formato e recusado")
    void cnpjOutsideTheFormatIsRejected() throws Exception {
        assertThat(createCompany(firstToken, "123").getResponse().getStatus()).isEqualTo(400);
        assertThat(createCompany(firstToken, "12.345.678/0001-99").getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("o mesmo CNPJ cabe em duas aplicacoes e colide dentro de uma")
    void sameCnpjFitsInTwoApplicationsAndCollidesInsideOne() throws Exception {
        String cnpj = uniqueCnpj();
        createCompany(firstToken, cnpj);

        assertThat(createCompany(secondToken, cnpj).getResponse().getStatus())
                .as("outra aplicacao").isEqualTo(201);
        assertThat(createCompany(firstToken, cnpj).getResponse().getStatus())
                .as("a mesma aplicacao").isEqualTo(409);
    }

    /**
     * O cadastro ja recusava CNPJ repetido, e a alteracao nao. Uma requisicao sequencial
     * basta: a restricao unica do banco recusa a linha, ninguem traduz a recusa, e a resposta
     * sai como erro de servidor, que nao esta na matriz e que o consumidor le como fonte
     * indisponivel. Corrigir um CNPJ digitado errado aparecia ao operador como servico fora
     * do ar.
     */
    @Test
    @DisplayName("mover o CNPJ para um que ja existe na mesma aplicacao responde conflito")
    void movingTheCnpjOntoAnExistingOneOfTheSameApplicationAnswersAsConflict() throws Exception {
        String occupied = uniqueCnpj();
        createCompany(firstToken, occupied);
        String identifier = JSON.readTree(createCompany(firstToken, uniqueCnpj())
                .getResponse().getContentAsString()).get("id").asString();

        MvcResult collided = mockMvc.perform(put("/api/companies/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa Exemplo\",\"cnpj\":\"" + occupied + "\"}"))
                .andReturn();

        assertThat(collided.getResponse().getStatus()).isEqualTo(409);
        JsonNode unchanged = JSON.readTree(mockMvc.perform(get("/api/companies/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andReturn().getResponse().getContentAsString());
        assertThat(unchanged.get("cnpj").asString())
                .as("o conflito nao grava metade da alteracao").isNotEqualTo(occupied);
    }

    @Test
    @DisplayName("alterar empresa de outra aplicacao responde como inexistente")
    void updatingACompanyOfAnotherApplicationAnswersAsNotFound() throws Exception {
        String foreignCnpj = uniqueCnpj();
        String foreignId = JSON.readTree(createCompany(secondToken, foreignCnpj)
                .getResponse().getContentAsString()).get("id").asString();

        int updated = mockMvc.perform(put("/api/companies/" + foreignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renomeada por quem nao e dono\",\"cnpj\":\""
                                + uniqueCnpj() + "\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(updated).isEqualTo(404);
        JsonNode untouched = JSON.readTree(mockMvc.perform(get("/api/companies/" + foreignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andReturn().getResponse().getContentAsString());
        assertThat(untouched.get("cnpj").asString())
                .as("o 404 e recusa, nao gravacao seguida de 404").isEqualTo(foreignCnpj);
    }

    @Test
    @DisplayName("a listagem mostra so as empresas da aplicacao que chamou")
    void listingShowsOnlyTheCompaniesOfTheCallingApplication() throws Exception {
        createCompany(firstToken, uniqueCnpj());
        String foreign = uniqueCnpj();
        createCompany(secondToken, foreign);

        JsonNode listing = JSON.readTree(mockMvc.perform(get("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andReturn().getResponse().getContentAsString());

        assertThat(listing).isNotEmpty();
        assertThat(listing.toString()).doesNotContain(foreign);
    }

    @Test
    @DisplayName("empresa de outra aplicacao responde como inexistente")
    void companyOfAnotherApplicationAnswersAsNotFound() throws Exception {
        MvcResult created = createCompany(secondToken, uniqueCnpj());
        String foreignId = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asString();

        int read = mockMvc.perform(get("/api/companies/" + foreignId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andReturn().getResponse().getStatus();
        int removed = mockMvc.perform(delete("/api/companies/" + foreignId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andReturn().getResponse().getStatus();

        assertThat(read).isEqualTo(404);
        assertThat(removed).isEqualTo(404);
    }
}
