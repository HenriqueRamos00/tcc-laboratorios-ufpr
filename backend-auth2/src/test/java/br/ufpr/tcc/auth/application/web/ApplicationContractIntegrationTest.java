package br.ufpr.tcc.auth.application.web;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * O cadastro de aplicacao, que e o registro de quem pode chamar o servico.
 *
 * Duas coisas aqui sao de contrato e nao de conveniencia. O codigo da aplicacao e unico, e
 * o segundo cadastro com o mesmo codigo precisa responder conflito, nao erro de servidor: a
 * matriz de codigos autoriza o 409 dentro da superficie administrativa, onde quem pergunta ja
 * pode ler a lista inteira. E a situacao da aplicacao decide o acesso dela no mesmo instante,
 * entao ela nao pode mudar por omissao de campo.
 */
@AutoConfigureMockMvc
class ApplicationContractIntegrationTest extends OracleIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong NEXT = new AtomicLong(990_000L);
    private static final String ADMINISTRATOR_PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String administrativeToken;
    private String code;

    @BeforeEach
    void seedAdministrator() throws Exception {
        long identifier = NEXT.incrementAndGet();
        String email = "cadastro." + identifier + "@lactec.exemplo";
        database.statement("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador', :email, LOWER(:email), TRUE, :hash,"
                        + " :now, :now)")
                .with("id", identifier).with("email", email)
                .with("hash", passwordEncoder.encode(ADMINISTRATOR_PASSWORD))
                .with("now", OffsetDateTime.now()).run();
        MvcResult signIn = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + ADMINISTRATOR_PASSWORD + "\"}"))
                .andReturn();
        administrativeToken = JSON.readTree(signIn.getResponse().getContentAsString())
                .get("accessToken").asString();
        code = "aplicacao-" + NEXT.incrementAndGet();
    }

    private MvcResult register(String applicationCode) throws Exception {
        return mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aplicacao\",\"code\":\"" + applicationCode + "\"}"))
                .andReturn();
    }

    @Test
    @DisplayName("codigo de aplicacao repetido responde conflito, e nao erro de servidor")
    void repeatedApplicationCodeAnswersConflict() throws Exception {
        assertThat(register(code).getResponse().getStatus()).isEqualTo(201);

        MvcResult repeated = register(code);

        assertThat(repeated.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("alterar uma aplicacao para o codigo de outra responde conflito")
    void takingTheCodeOfAnotherApplicationAnswersConflict() throws Exception {
        register(code);
        String otherCode = code + "-outra";
        String otherId = JSON.readTree(register(otherCode).getResponse().getContentAsString())
                .get("id").asString();

        MvcResult collision = mockMvc.perform(put("/api/services/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aplicacao\",\"code\":\"" + code
                                + "\",\"active\":true}"))
                .andReturn();

        assertThat(collision.getResponse().getStatus()).isEqualTo(409);
    }

    /**
     * A situacao da aplicacao corta o acesso dela no mesmo instante, entao ela nao pode voltar
     * por omissao de campo.
     *
     * Medido antes da correcao: um PUT enviado para corrigir o nome de uma aplicacao desativada
     * a devolvia ao ar, porque o campo ausente valia verdadeiro. Quem desativou nao pediu isso,
     * e quem corrigiu o nome nao soube que reabriu o acesso.
     */
    @Test
    @DisplayName("alterar aplicacao sem declarar a situacao e recusado, e nao reativa em silencio")
    void updateWithoutTheSituationIsRefusedInsteadOfReactivating() throws Exception {
        String identifier = JSON.readTree(register(code).getResponse().getContentAsString())
                .get("id").asString();
        mockMvc.perform(delete("/api/services/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();

        MvcResult withoutSituation = mockMvc.perform(put("/api/services/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nome corrigido\",\"code\":\"" + code + "\"}"))
                .andReturn();

        assertThat(withoutSituation.getResponse().getStatus()).isEqualTo(400);
        MvcResult afterwards = mockMvc.perform(get("/api/services/" + identifier)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administrativeToken))
                .andReturn();
        assertThat(JSON.readTree(afterwards.getResponse().getContentAsString())
                .get("active").asBoolean())
                .as("a aplicacao continua desativada").isFalse();
    }
}
