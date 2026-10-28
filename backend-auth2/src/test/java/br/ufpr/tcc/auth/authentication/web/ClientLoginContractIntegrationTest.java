package br.ufpr.tcc.auth.authentication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.authentication.LoginAttemptLedger;
import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Teste de contrato da matriz de codigos de resposta.
 *
 * O que ele protege nao e o caminho feliz, e sim a indistinguibilidade: os casos que
 * compartilham resposta sao comparados inteiros, cabecalhos incluidos. A comparacao
 * normaliza a data e mascara o identificador de correlacao, porque esses dois mudam a cada
 * requisicao por projeto; exigir igualdade literal faria alguem remover a correlacao para
 * o teste passar, e a trilha perderia o campo que liga as pontas.
 */
@AutoConfigureMockMvc
class ClientLoginContractIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT_ID = new AtomicLong(800_000L);
    private static final String GOOD_PASSWORD = "Senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptLedger ledger;

    private record Registered(String serviceToken, String applicationCode, String email) {
    }

    private Registered registerApplicationWithAccount(String code, boolean confirmed, boolean active) {
        long applicationId = NEXT_ID.incrementAndGet();
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", applicationId).param("code", code)
                .param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT_ID.incrementAndGet()).param("applicationId", applicationId)
                .param("digest", token.digest().asBytes()).param("now", OffsetDateTime.now()).update();
        String email = "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
        jdbcClient.sql("INSERT INTO CLIENT_ACCOUNT (ID, APPLICATION_ID, NAME, EMAIL, EMAIL_NORMALIZED,"
                        + " PASSWORD_HASH, EMAIL_CONFIRMED, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, 'Pessoa de Teste', :email, :email, :hash,"
                        + " :confirmed, :active, :now, :now)")
                .param("id", NEXT_ID.incrementAndGet()).param("applicationId", applicationId)
                .param("email", email).param("hash", passwordEncoder.encode(GOOD_PASSWORD))
                .param("confirmed", confirmed ? 1 : 0).param("active", active ? 1 : 0)
                .param("now", OffsetDateTime.now()).update();
        return new Registered(token.clearValue(), code, email);
    }

    private MvcResult login(String serviceToken, String email, String password) throws Exception {
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        if (serviceToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** Forma comparavel da resposta, com o que muda por requisicao normalizado. */
    private static String comparableForm(MvcResult result) throws Exception {
        StringBuilder form = new StringBuilder();
        form.append(result.getResponse().getStatus()).append('\n');
        result.getResponse().getHeaderNames().stream().sorted().forEach(name -> {
            String value = "Date".equalsIgnoreCase(name) || "X-Request-Id".equalsIgnoreCase(name)
                    ? "<variavel por requisicao>" : String.join(",", result.getResponse().getHeaders(name));
            form.append(name).append(": ").append(value).append('\n');
        });
        form.append(result.getResponse().getContentAsString());
        return form.toString();
    }

    @Test
    @DisplayName("credencial correta responde 200 com a identidade que o consumidor exige")
    void correctCredentialAnswersWithTheIdentityTheConsumerRequires() throws Exception {
        Registered registered = registerApplicationWithAccount("portal-contrato", true, true);

        MvcResult result = login(registered.serviceToken(), registered.email(), GOOD_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"role\":\"CLIENTE\"")
                .contains("\"serviceCode\":\"portal-contrato\"")
                .contains("\"companyCnpjs\"")
                .doesNotContain("passwordHash").doesNotContain("Senha123");
        assertThat(body).matches(".*\"id\":\"[0-9]+\".*");
    }

    // O caso central: cinco motivos de recusa, uma resposta so, comparada inteira.
    @Test
    @DisplayName("os cinco motivos de recusa respondem identicamente, cabecalhos incluidos")
    void everyRejectionReasonAnswersIdentically() throws Exception {
        Registered base = registerApplicationWithAccount("portal-identico", true, true);
        Registered unconfirmed = registerApplicationWithAccount("portal-identico-b", false, true);
        Registered deactivated = registerApplicationWithAccount("portal-identico-c", true, false);
        Registered neighbour = registerApplicationWithAccount("portal-identico-d", true, true);

        String wrongPassword = comparableForm(login(base.serviceToken(), base.email(), "Errada123"));
        String unknownEmail =
                comparableForm(login(base.serviceToken(), "ninguem@exemplo.com", GOOD_PASSWORD));
        String otherApplication =
                comparableForm(login(base.serviceToken(), neighbour.email(), GOOD_PASSWORD));
        String notConfirmed =
                comparableForm(login(unconfirmed.serviceToken(), unconfirmed.email(), GOOD_PASSWORD));
        String inactive =
                comparableForm(login(deactivated.serviceToken(), deactivated.email(), GOOD_PASSWORD));

        assertThat(wrongPassword).startsWith("401").doesNotContain("WWW-Authenticate");
        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(otherApplication).isEqualTo(wrongPassword);
        assertThat(notConfirmed).isEqualTo(wrongPassword);
        assertThat(inactive).isEqualTo(wrongPassword);
    }

    // Os dois 401 saem de lugares diferentes e diferem por um cabecalho, e isso foi
    // medido no consumidor: com o desafio, ele trata senha errada como fonte
    // indisponivel e a tela mostra erro de infraestrutura.
    @Test
    @DisplayName("falha da credencial da aplicacao responde 401 com desafio")
    void applicationCredentialFailureAnswersWithChallenge() throws Exception {
        Registered registered = registerApplicationWithAccount("portal-desafio", true, true);

        MvcResult withoutToken = login(null, registered.email(), GOOD_PASSWORD);
        MvcResult withUnknownToken =
                login(ServiceToken.generate().clearValue(), registered.email(), GOOD_PASSWORD);

        assertThat(withoutToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(withoutToken.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer");
        assertThat(withUnknownToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(withUnknownToken.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer");
    }

    @Test
    @DisplayName("a quinta falha responde 429 com prazo em segundos inteiros")
    void fifthFailureAnswersWithRetryAfterInWholeSeconds() throws Exception {
        Registered registered = registerApplicationWithAccount("portal-429", true, true);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(login(registered.serviceToken(), registered.email(), "Errada123")
                    .getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult fifth = login(registered.serviceToken(), registered.email(), "Errada123");

        assertThat(fifth.getResponse().getStatus()).isEqualTo(429);
        String retryAfter = fifth.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).matches("[0-9]+");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 86400L);
        assertThat(fifth.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    /**
     * A segunda metade do nome do caso tambem precisa ser afirmada. Sem conferir o contador,
     * um 400 que debitasse tentativa passaria, e corpo invalido repetido viraria o jeito mais
     * barato de trancar conta alheia.
     */
    @Test
    @DisplayName("corpo malformado responde 400 e nao gasta tentativa")
    void malformedBodyAnswersBadRequest() throws Exception {
        Registered registered = registerApplicationWithAccount("portal-400", true, true);

        MvcResult missingField = mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.serviceToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@y.com\"}"))
                .andReturn();
        MvcResult unreadable = mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.serviceToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{isto nao e json}"))
                .andReturn();

        assertThat(missingField.getResponse().getStatus()).isEqualTo(400);
        assertThat(unreadable.getResponse().getStatus())
                .as("corpo ilegivel e campo faltando sao o mesmo desfecho nesta rota")
                .isEqualTo(400);
        assertThat(attemptsOf(registered))
                .as("nenhuma das duas recusas de corpo debita tentativa").isZero();
    }

    /**
     * No login nao existe minimo de senha, e este caso existe para proteger essa ausencia.
     *
     * Cobrar oito caracteres aqui responderia 400 onde o contrato espera 401, entregaria o
     * tamanho da senha a quem sonda e deixaria a tentativa curta sem gastar uma das cinco.
     * Medido antes deste caso: nenhuma chamada da suite as duas rotas de login usava senha
     * com menos de oito caracteres, entao acrescentar a restricao de tamanho no pedido
     * passava sem nenhum teste cair.
     */
    @Test
    @DisplayName("senha curta no login responde 401 como senha errada, e nao 400")
    void shortPasswordOnLoginAnswersUnauthorizedAndNotBadRequest() throws Exception {
        Registered registered = registerApplicationWithAccount("portal-senha-curta", true, true);

        MvcResult tooShort = login(registered.serviceToken(), registered.email(), "Ab1");
        MvcResult simplyWrong = login(registered.serviceToken(), registered.email(), "Errada123");

        assertThat(tooShort.getResponse().getStatus()).isEqualTo(401);
        assertThat(tooShort.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .as("recusa de senha nao desafia").isNull();
        assertThat(comparableForm(tooShort))
                .as("senha curta e senha errada sao a mesma resposta, byte a byte")
                .isEqualTo(comparableForm(simplyWrong));
        assertThat(attemptsOf(registered))
                .as("as duas tentativas curtas e erradas contam igual").isEqualTo(2);
    }

    /**
     * Entrar com a senha certa precisa zerar a contagem **pela rota**, e nao apenas no
     * contador chamado direto.
     *
     * O caso que existia chamava o contador diretamente, entao a fiacao entre o caso de uso e
     * ele nunca era exercitada: apagar a linha que zera deixava a suite verde, e o titular que
     * entrasse depois de quatro erros ficaria com quatro erros guardados, trancando a conta no
     * erro de digitacao seguinte, que e exatamente o que a funcao existe para evitar.
     */
    @Test
    @DisplayName("entrar com a senha certa zera a contagem de tentativa")
    void successfulSignInClearsTheFailureCount() throws Exception {
        Registered registered = registerApplicationWithAccount("portal-zera", true, true);
        for (int attempt = 1; attempt <= 4; attempt++) {
            login(registered.serviceToken(), registered.email(), "Errada123");
        }

        assertThat(login(registered.serviceToken(), registered.email(), GOOD_PASSWORD)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(attemptsOf(registered)).as("a contagem volta a zero").isZero();

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(login(registered.serviceToken(), registered.email(), "Errada123")
                    .getResponse().getStatus())
                    .as("tentativa " + attempt + " depois do acerto").isEqualTo(401);
        }
    }

    /**
     * Tentativas debitadas para aquela conta naquela aplicacao, perguntadas ao contador real.
     *
     * A contagem vem da producao de proposito. Recalcular a chave do contador dentro do teste
     * faria ele concordar com uma mudanca de formato de chave em vez de acusa-la, que foi
     * exatamente o defeito encontrado no caso irmao da entrada administrativa.
     */
    private int attemptsOf(Registered registered) {
        long applicationId = jdbcClient.sql("SELECT ID FROM APPLICATION WHERE CODE = :code")
                .param("code", registered.applicationCode()).query(Long.class).single();
        return ledger.failedAttemptsOf(applicationId, registered.email());
    }

    @Test
    @DisplayName("a sonda de disponibilidade responde sem credencial nenhuma")
    void healthProbeAnswersWithoutCredential() throws Exception {
        MvcResult health = mockMvc.perform(get("/health")).andReturn();

        assertThat(health.getResponse().getStatus()).isEqualTo(200);
        assertThat(health.getResponse().getContentAsString()).contains("\"status\":\"UP\"");
    }

    // O Spring publica POST /login e um usuario padrao quando formLogin nao e
    // desligado, e essa rota responderia 302 fora desta matriz.
    @Test
    @DisplayName("rota de formulario do framework nao existe")
    void frameworkFormLoginRouteDoesNotExist() throws Exception {
        MvcResult formLogin = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("username=user&password=x")).andReturn();

        assertThat(formLogin.getResponse().getStatus()).isNotEqualTo(302);
        assertThat(formLogin.getResponse().getStatus()).isEqualTo(401);
    }
}
