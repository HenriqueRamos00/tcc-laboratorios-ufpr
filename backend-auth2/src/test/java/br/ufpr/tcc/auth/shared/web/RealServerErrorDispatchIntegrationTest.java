package br.ufpr.tcc.auth.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste contra um servidor de verdade, e nao contra o simulador de requisicao.
 *
 * Ele existe por causa de um defeito que so apareceu rodando a aplicacao: falha de
 * validacao respondia **401 com desafio** em vez de 400, porque o erro vira um segundo
 * despacho dentro do contentor, e esse despacho passava pela cadeia de seguranca e era
 * recusado. O simulador de requisicao nao reproduz esse segundo despacho, entao a suite
 * inteira ficava verde com a aplicacao quebrada.
 *
 * A licao fica registrada no nome da classe: o que o simulador nao enxerga precisa de um
 * caso que suba o servidor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerErrorDispatchIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(50_000L);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADMINISTRATOR_PASSWORD = "SenhaForte123";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext context;

    private RestClient client;
    private String applicationToken;

    @BeforeEach
    void prepare() {
        client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        long applicationId = NEXT.incrementAndGet();
        String code = "despacho-" + applicationId;
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", applicationId).param("code", code)
                .param("now", OffsetDateTime.now()).update();
        ServiceToken token = ServiceToken.generate();
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .param("id", NEXT.incrementAndGet()).param("applicationId", applicationId)
                .param("digest", token.digest().asBytes())
                .param("now", OffsetDateTime.now()).update();
        applicationToken = token.clearValue();
    }

    private HttpStatusCode postStatus(String path, String body) {
        return client.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpHeaders postHeaders(String path, String body) {
        return client.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> response.getHeaders());
    }

    @Test
    @DisplayName("campo invalido responde 400, e nao 401 com desafio")
    void invalidFieldAnswersBadRequestAndNotUnauthorized() {
        HttpStatusCode status = postStatus("/api/companies",
                "{\"name\":\"Empresa\",\"cnpj\":\"12.345.678/0001-99\"}");

        assertThat(status.value()).isEqualTo(400);
        assertThat(postHeaders("/api/companies", "{\"name\":\"Empresa\",\"cnpj\":\"123\"}")
                .getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .as("falha de validacao nao e falha de credencial").isNull();
    }

    @Test
    @DisplayName("corpo ilegivel responde 400, e nao 401 com desafio")
    void unreadableBodyAnswersBadRequest() {
        HttpStatusCode status = postStatus("/api/companies", "{isto nao e json}");

        assertThat(status.value()).isEqualTo(400);
    }

    @Test
    @DisplayName("a sonda de prontidao responde sem credencial nenhuma")
    void readinessProbeAnswersWithoutCredential() {
        HttpStatusCode status = client.get().uri("/health/readiness")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value())
                .as("o healthcheck do contentor usa este caminho").isEqualTo(200);
    }

    @Test
    @DisplayName("caminho que nao existe responde como inexistente, sem desafio")
    void unknownPathAnswersAsNotFound() {
        HttpStatusCode status = client.get().uri("/api/rota-que-nao-existe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken)
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(404);
    }

    /**
     * Status, desafio e Allow da mesma resposta, copiados antes de o corpo ser fechado.
     *
     * Os tres juntos porque a matriz compara irmaos inteiros, e porque e o Allow que decide
     * se a resposta confirma a existencia do caminho a quem perguntou.
     */
    private record Observed(int status, String challenge, String allow) {
    }

    private Observed observe(HttpMethod method, String path, String token) {
        RestClient.RequestBodySpec request = client.method(method).uri(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange((outgoing, response) -> new Observed(
                response.getStatusCode().value(),
                response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE),
                response.getHeaders().getFirst(HttpHeaders.ALLOW)));
    }

    /**
     * Semeia um administrador proprio e entra com ele dentro do caso que o usa.
     *
     * Nao em BeforeAll de proposito: outra classe da suite apaga a tabela de administradores
     * no BeforeAll dela, e depender da ordem entre classes seria defeito silencioso ate o dia
     * em que a ordem muda.
     */
    private String administrativeToken() {
        long administratorId = NEXT.incrementAndGet();
        String email = "despacho." + administratorId + "@lactec.exemplo";
        jdbcClient.sql("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador do despacho', :email, :email, TRUE,"
                        + " :hash, :now, :now)")
                .param("id", administratorId).param("email", email)
                .param("hash", passwordEncoder.encode(ADMINISTRATOR_PASSWORD))
                .param("now", OffsetDateTime.now()).update();
        String body = client.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + ADMINISTRATOR_PASSWORD
                        + "\"}")
                .retrieve().body(String.class);
        return JSON.readTree(body).get("accessToken").asString();
    }

    /**
     * As duas rotas abertas sao liberadas por verbo, e nao por caminho.
     *
     * Medido na aplicacao no ar antes da correcao: OPTIONS respondia 200 com
     * "Allow: POST,OPTIONS" e GET respondia 405 com "Allow: POST", enquanto caminho inventado
     * respondia 401. Allow confirma que o caminho existe, e na rota de instalacao o par de
     * respostas ainda contava se a instalacao ja havia sido feita.
     */
    @Test
    @DisplayName("as rotas abertas nao se revelam a quem nao apresentou credencial")
    void openRoutesAreNotDiscoverableWithoutCredential() {
        Observed inventedPath = observe(HttpMethod.OPTIONS, "/api/rota-que-nao-existe", null);

        for (String openRoute : List.of("/api/setup/admin", "/api/admin/auth/login")) {
            for (HttpMethod method : List.of(HttpMethod.OPTIONS, HttpMethod.GET)) {
                Observed observed = observe(method, openRoute, null);
                assertThat(observed.status()).as("%s %s", method, openRoute)
                        .isEqualTo(inventedPath.status());
                assertThat(observed.allow()).as("Allow confirma o caminho: %s %s", method,
                        openRoute).isNull();
                assertThat(observed.challenge()).as("%s %s", method, openRoute)
                        .isEqualTo(inventedPath.challenge());
            }
        }
    }

    /**
     * O caso sem credencial das quatro rotas de administrador. O contrato exige um para cada
     * rota, e o cadastro de administrador entrou depois das outras classes.
     */
    @Test
    @DisplayName("sem credencial, as rotas de administrador respondem 401 com desafio")
    void administratorRoutesAnswerUnauthorizedWithChallenge() {
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
            Observed observed = observe(method, "/api/admins", null);
            assertThat(observed.status()).as("%s /api/admins", method).isEqualTo(401);
            assertThat(observed.challenge()).as("%s /api/admins", method).startsWith("Bearer");
        }
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE)) {
            Observed observed = observe(method, "/api/admins/900001", null);
            assertThat(observed.status()).as("%s /api/admins/{id}", method).isEqualTo(401);
            assertThat(observed.challenge()).as("%s /api/admins/{id}", method)
                    .startsWith("Bearer");
        }
    }

    /**
     * A decisao sobre 405 fixada nos tres lados dela.
     *
     * Este caso tambem mede se o Allow sobrevive ao segundo despacho do contentor: o
     * resolvedor grava o cabecalho e depois pede a pagina de erro, e a norma manda que todo
     * 405 leve Allow.
     */
    @Test
    @DisplayName("verbo inexistente: 405 com Allow so para quem ja e administrador")
    void methodNotAllowedCarriesAllowOnlyForTheAdministrator() {
        Observed anonymous = observe(HttpMethod.PATCH, "/api/admins", null);
        Observed withApplication = observe(HttpMethod.PATCH, "/api/admins", applicationToken);
        String administrativeToken = administrativeToken();
        Observed postOnItem = observe(HttpMethod.POST, "/api/admins/900001", administrativeToken);
        Observed patchOnCollection = observe(HttpMethod.PATCH, "/api/admins", administrativeToken);

        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.allow()).as("sem credencial nao se descobre verbo").isNull();
        assertThat(withApplication.status()).isEqualTo(403);
        assertThat(withApplication.allow())
                .as("credencial de aplicacao nao descobre a superficie administrativa").isNull();
        assertThat(postOnItem.status()).isEqualTo(405);
        assertThat(postOnItem.allow()).contains("GET").contains("PUT").contains("DELETE");
        assertThat(patchOnCollection.status()).isEqualTo(405);
        assertThat(patchOnCollection.allow()).contains("GET").contains("POST");
    }

    /**
     * A cadeia desliga o encerramento de sessao do framework de forma explicita. Sem isso, e
     * com a protecao de formulario desligada, o filtro dele deixa de exigir POST e passa a
     * casar GET tambem, e ele roda antes da autorizacao: um anonimo receberia 302, status que
     * nao existe na matriz e que no consumidor vira fonte indisponivel.
     */
    @Test
    @DisplayName("a rota de encerramento de sessao do framework nao existe")
    void frameworkLogoutRouteDoesNotExist() {
        Observed inventedPath = observe(HttpMethod.GET, "/api/rota-que-nao-existe", null);

        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
            Observed logout = observe(method, "/logout", null);
            assertThat(logout.status()).as("%s /logout, 302 esta fora da matriz", method)
                    .isEqualTo(inventedPath.status());
        }
    }

    /**
     * A documentacao gerada lista a superficie administrativa inteira e o botao de
     * experimentar da interface gasta tentativa da conta que ele usar. Ela fica desligada por
     * propriedade, e este caso avisa quando alguem liga sem querer ou quando a biblioteca
     * muda a condicao de alguma dessas rotas.
     *
     * O ultimo caminho e de outra natureza e nao e governado por propriedade do springdoc: os
     * arquivos do webjar da interface chegam pelo mapeamento estatico padrao do servidor, que
     * este servico desliga porque serve JSON e nada mais. O caminho vai sem numero de versao
     * de proposito, para o caso nao quebrar quando a versao do webjar mudar.
     */
    @Test
    @DisplayName("a documentacao gerada nao e servida pela aplicacao")
    void generatedDocumentationIsNotServed() {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml",
                "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html",
                "/webjars/swagger-ui/index.html")) {
            assertThat(observe(HttpMethod.GET, path, applicationToken).status())
                    .as("caminho da documentacao gerada ainda servido: %s", path).isEqualTo(404);
        }
    }

    /**
     * Credencial administrativa em rota que exige escopo de aplicacao.
     *
     * Estas rotas leem o chamador como aplicacao para derivar o escopo. Com credencial
     * administrativa a identidade nao e do tipo esperado, o resolvedor do framework entrega
     * nulo, e o que sai e erro de servidor: status fora da matriz, pilha no log, e no
     * consumidor a leitura de fonte indisponivel. A cadeia precisa recusar antes do despacho.
     */
    @Test
    @DisplayName("credencial administrativa em rota de aplicacao e recusada, e nunca da 5xx")
    void administrativeCredentialIsRefusedOnApplicationScopedRoutes() throws Exception {
        String administrativeToken = administrativeToken();

        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
            Observed observed = observe(method, "/api/companies", administrativeToken);
            assertThat(observed.status()).as("%s /api/companies", method).isEqualTo(403);
        }
        for (String path : List.of("/api/auth/login", "/api/auth/recoveries", "/api/users")) {
            Observed observed = observe(HttpMethod.POST, path, administrativeToken);
            assertThat(observed.status()).as("POST %s", path).isEqualTo(403);
        }
    }

    /**
     * A exclusao da configuracao automatica do usuario em memoria e o que impede a linha
     * "Using generated security password" no log de toda subida. Sem este caso, remover a
     * exclusao deixa a suite verde e devolve a credencial ao log.
     */
    @Test
    @DisplayName("nenhum usuario em memoria e publicado, logo nenhuma senha vai para o log")
    void noInMemoryUserIsPublished() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class))
                .as("quem autentica sao os dois diretorios de token opaco").isEmpty();
    }
}
