package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A regra 3 do Lactec, maximo de cinco tentativas, aplicada a entrada administrativa.
 *
 * O login de cliente ja tinha bloqueio. O administrativo nao tinha, e ele e a porta que abre
 * todas as outras: quem entra por ali cadastra aplicacao, emite credencial e le a lista de
 * administradores. A rota tambem e aberta na cadeia, sem credencial de aplicacao, entao
 * tentativa ilimitada ali nao exige nem estar integrado ao servico.
 *
 * Cada tentativa custa uma verificacao Argon2id, que foi dimensionada para custar caro de
 * proposito. Sem contador, isso nao e so adivinhacao de senha: e consumo de memoria do
 * processo a pedido de quem chama.
 */
@AutoConfigureMockMvc
class AdministrativeLoginLockOutIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(980_000L);
    private static final String PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void seedAdministrator() {
        long identifier = NEXT.incrementAndGet();
        email = "bloqueio." + identifier + "@lactec.exemplo";
        database.statement("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador do bloqueio', :email, LOWER(:email), TRUE,"
                        + " :hash, :now, :now)")
                .with("id", identifier).with("email", email)
                .with("hash", passwordEncoder.encode(PASSWORD))
                .with("now", OffsetDateTime.now()).run();
    }

    private MvcResult attempt(String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    /** Valor limite da regra: a quarta ainda recusa, a quinta bloqueia, a sexta segue bloqueada. */
    @Test
    @DisplayName("da quinta tentativa em diante a entrada administrativa responde 429")
    void administrativeSignInLocksOutOnTheFifthAttempt() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            MvcResult refused = attempt("ErradaTotal999");
            assertThat(refused.getResponse().getStatus())
                    .as("tentativa %s ainda e recusa comum", attempt).isEqualTo(401);
            assertThat(refused.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .as("credencial de pessoa nao leva desafio").isNull();
        }

        MvcResult fifth = attempt("ErradaTotal999");
        MvcResult sixth = attempt("ErradaTotal999");

        assertThat(fifth.getResponse().getStatus()).isEqualTo(429);
        assertThat(fifth.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .as("o cliente le o tempo de espera em segundos")
                .isNotNull().matches("[1-9][0-9]*");
        assertThat(sixth.getResponse().getStatus()).isEqualTo(429);
    }

    /**
     * Bloqueada e bloqueada tambem para quem sabe a senha, senao o contador nao protege nada:
     * bastaria intercalar a senha certa para zerar.
     */
    @Test
    @DisplayName("bloqueada, a senha certa tambem nao entra enquanto o prazo corre")
    void theRightPasswordDoesNotPassWhileLockedOut() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            attempt("ErradaTotal999");
        }

        MvcResult withTheRightPassword = attempt(PASSWORD);

        assertThat(withTheRightPassword.getResponse().getStatus()).isEqualTo(429);
    }

    /**
     * O contador de administrador nao pode compartilhar espaco com o de cliente.
     *
     * Se compartilhasse, um cliente de qualquer aplicacao com o mesmo endereco de e-mail de
     * um administrador poderia tranca lo de fora, e o contrario tambem.
     *
     * A versao anterior deste caso conferia que a chave digerida do cliente nao tinha linha
     * no contador, e nao provava nada: o identificador de aplicacao usado na chave era
     * inventado dentro do proprio teste e nunca apresentado a producao, entao a contagem era
     * zero por construcao, em qualquer implementacao. Pior, o auxiliar reimplementava o
     * algoritmo da chave, e trocar o separador na producao deixava tudo verde porque a copia
     * mudava junto.
     *
     * Entrar pela rota conserta as duas coisas: o cliente entra de verdade depois de o
     * administrador estar bloqueado, e e a producao que decide a chave.
     */
    @Test
    @DisplayName("bloquear o administrador nao impede o cliente de mesmo endereco de entrar")
    void lockingTheAdministratorDoesNotStopTheClientWithTheSameAddress() throws Exception {
        long applicationId = NEXT.incrementAndGet();
        String code = "bloqueio-" + applicationId;
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", applicationId).with("code", code)
                .with("now", OffsetDateTime.now()).run();
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT) VALUES (:id, :applicationId, 'token', :digest, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", applicationId)
                .with("digest", token.digest().asBytes())
                .with("now", OffsetDateTime.now()).run();
        database.statement("INSERT INTO CLIENT_ACCOUNT (ID, APPLICATION_ID, NAME, EMAIL,"
                        + " EMAIL_NORMALIZED, PASSWORD_HASH, EMAIL_CONFIRMED, ACTIVE,"
                        + " CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, 'Cliente de mesmo endereco', :email,"
                        + " LOWER(:email), :hash, 1, 1, :now, :now)")
                .with("id", NEXT.incrementAndGet()).with("applicationId", applicationId)
                .with("email", email).with("hash", passwordEncoder.encode(PASSWORD))
                .with("now", OffsetDateTime.now()).run();

        for (int attempt = 1; attempt <= 5; attempt++) {
            attempt("ErradaTotal999");
        }
        int administrativeEntry = attempt(PASSWORD).getResponse().getStatus();
        int clientEntry = mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.clearValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(administrativeEntry).as("o administrador esta bloqueado").isEqualTo(429);
        assertThat(clientEntry).as("e o cliente de mesmo endereco entra normalmente")
                .isEqualTo(200);
    }
}
