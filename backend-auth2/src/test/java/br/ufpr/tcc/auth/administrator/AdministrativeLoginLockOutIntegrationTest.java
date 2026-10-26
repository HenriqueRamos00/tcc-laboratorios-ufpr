package br.ufpr.tcc.auth.administrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void seedAdministrator() {
        long identifier = NEXT.incrementAndGet();
        email = "bloqueio." + identifier + "@lactec.exemplo";
        jdbcClient.sql("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador do bloqueio', :email, LOWER(:email), TRUE,"
                        + " :hash, :now, :now)")
                .param("id", identifier).param("email", email)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .param("now", OffsetDateTime.now()).update();
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
     * O contador de administrador nao pode compartilhar espaco com o de cliente. Se
     * compartilhasse, um cliente de qualquer aplicacao com o mesmo endereco de e-mail de um
     * administrador poderia tranca lo de fora, e o contrario tambem.
     */
    @Test
    @DisplayName("bloquear o administrador nao bloqueia o cliente de mesmo endereco")
    void administrativeLockOutDoesNotReachTheClientWithTheSameAddress() throws Exception {
        long applicationId = NEXT.incrementAndGet();
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", applicationId).param("code", "bloqueio-" + applicationId)
                .param("now", OffsetDateTime.now()).update();
        for (int attempt = 1; attempt <= 5; attempt++) {
            attempt("ErradaTotal999");
        }

        int clientFailures = jdbcClient
                .sql("SELECT COUNT(*) FROM LOGIN_ATTEMPT WHERE ATTEMPT_KEY = :key")
                .param("key", clientAttemptKey(applicationId, email))
                .query(Integer.class).single();

        assertThat(clientFailures).as("o contador do cliente daquela aplicacao nao foi tocado")
                .isZero();
    }

    private static byte[] clientAttemptKey(long applicationId, String identifier) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return digest.digest((applicationId + "\u0000"
                    + identifier.toLowerCase(java.util.Locale.ROOT))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 precisa existir na plataforma", absent);
        }
    }
}
