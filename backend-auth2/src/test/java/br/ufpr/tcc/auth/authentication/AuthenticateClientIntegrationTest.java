package br.ufpr.tcc.auth.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prova a decisao de login contra um Oracle de verdade.
 *
 * O ponto central desta classe nao e o caminho feliz: e que todos os caminhos de recusa
 * devolvem o mesmo desfecho. Senha errada, conta inexistente, conta de outra aplicacao,
 * conta nao confirmada e conta desativada precisam ser indistinguiveis, e aqui isso e
 * afirmado comparando os desfechos entre si, nao conferindo cada um isoladamente.
 *
 * Os casos nao sao transacionais porque o contador de tentativa grava em transacao propria
 * e precisa sobreviver a chamada.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthenticateClientIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT_ID = new AtomicLong(700_000L);
    private static final String GOOD_PASSWORD = "Senha123";

    @Autowired
    private AuthenticateClient authenticateClient;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApplicationCaller application(String code) {
        long id = NEXT_ID.incrementAndGet();
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :name, TRUE, :now, :now)")
                .param("id", id).param("code", code).param("name", code)
                .param("now", OffsetDateTime.now()).update();
        return new ApplicationCaller(id, code);
    }

    private String account(ApplicationCaller caller, String email, boolean confirmed, boolean active) {
        jdbcClient.sql("INSERT INTO CLIENT_ACCOUNT (ID, APPLICATION_ID, NAME, EMAIL, EMAIL_NORMALIZED,"
                        + " PASSWORD_HASH, EMAIL_CONFIRMED, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :name, :email, :normalized, :hash,"
                        + " :confirmed, :active, :now, :now)")
                .param("id", NEXT_ID.incrementAndGet()).param("applicationId", caller.applicationId())
                .param("name", "Pessoa de Teste").param("email", email)
                .param("normalized", email.toLowerCase(java.util.Locale.ROOT))
                .param("hash", passwordEncoder.encode(GOOD_PASSWORD))
                .param("confirmed", confirmed ? 1 : 0).param("active", active ? 1 : 0)
                .param("now", OffsetDateTime.now()).update();
        return email;
    }

    private String uniqueEmail() {
        return "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
    }

    @Test
    @DisplayName("credencial correta autentica e devolve a identidade que o consumidor espera")
    void correctCredentialAuthenticates() {
        ApplicationCaller caller = application("portal-ok");
        String email = account(caller, uniqueEmail(), true, true);

        ClientAuthentication result = authenticateClient.authenticate(caller, email, GOOD_PASSWORD);

        assertThat(result).isInstanceOf(ClientAuthentication.Authenticated.class);
        ClientAuthentication.Authenticated authenticated = (ClientAuthentication.Authenticated) result;
        assertThat(authenticated.identity().name()).isEqualTo("Pessoa de Teste");
        assertThat(authenticated.identity().serviceCode()).isEqualTo("portal-ok");
        assertThat(authenticated.identity().role()).isEqualTo("CLIENTE");
    }

    // Este e o caso central da fatia: cinco motivos de recusa, um desfecho so.
    @Test
    @DisplayName("todos os motivos de recusa produzem exatamente o mesmo desfecho")
    void everyRejectionReasonProducesTheSameOutcome() {
        ApplicationCaller caller = application("portal-uniforme");
        ApplicationCaller other = application("portal-vizinho");
        String wrongPassword = account(caller, uniqueEmail(), true, true);
        String unconfirmed = account(caller, uniqueEmail(), false, true);
        String deactivated = account(caller, uniqueEmail(), true, false);
        String inAnotherApplication = account(other, uniqueEmail(), true, true);

        ClientAuthentication byWrongPassword = authenticateClient.authenticate(caller, wrongPassword, "Errada123");
        ClientAuthentication byUnknown = authenticateClient.authenticate(caller, uniqueEmail(), GOOD_PASSWORD);
        ClientAuthentication byOtherApplication =
                authenticateClient.authenticate(caller, inAnotherApplication, GOOD_PASSWORD);
        ClientAuthentication byUnconfirmed = authenticateClient.authenticate(caller, unconfirmed, GOOD_PASSWORD);
        ClientAuthentication byDeactivated = authenticateClient.authenticate(caller, deactivated, GOOD_PASSWORD);

        assertThat(byWrongPassword).isEqualTo(ClientAuthentication.rejected());
        assertThat(byUnknown).isEqualTo(byWrongPassword);
        assertThat(byOtherApplication).isEqualTo(byWrongPassword);
        assertThat(byUnconfirmed).isEqualTo(byWrongPassword);
        assertThat(byDeactivated).isEqualTo(byWrongPassword);
    }

    // As cinco tentativas sao todas avaliadas: a quinta com senha certa autentica.
    @Test
    @DisplayName("senha certa na quinta tentativa autentica e zera a contagem")
    void correctPasswordOnTheFifthAttemptStillAuthenticates() {
        ApplicationCaller caller = application("portal-quinta");
        String email = account(caller, uniqueEmail(), true, true);
        for (int attempt = 1; attempt <= 4; attempt++) {
            authenticateClient.authenticate(caller, email, "Errada123");
        }

        assertThat(authenticateClient.authenticate(caller, email, GOOD_PASSWORD))
                .isInstanceOf(ClientAuthentication.Authenticated.class);
    }

    @Test
    @DisplayName("a quinta falha bloqueia e informa por quanto tempo")
    void fifthFailureLocksAndTellsForHowLong() {
        ApplicationCaller caller = application("portal-bloqueio");
        String email = account(caller, uniqueEmail(), true, true);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(authenticateClient.authenticate(caller, email, "Errada123"))
                    .isEqualTo(ClientAuthentication.rejected());
        }

        ClientAuthentication fifth = authenticateClient.authenticate(caller, email, "Errada123");

        assertThat(fifth).isInstanceOf(ClientAuthentication.Locked.class);
        assertThat(((ClientAuthentication.Locked) fifth).retryAfterSeconds()).isPositive();
    }

    // Bloqueio que cede a senha certa nao e bloqueio: quem adivinhou a senha na
    // sexta tentativa entraria.
    @Test
    @DisplayName("conta bloqueada recusa ate a senha certa")
    void lockedAccountRefusesEvenTheCorrectPassword() {
        ApplicationCaller caller = application("portal-trancado");
        String email = account(caller, uniqueEmail(), true, true);
        for (int attempt = 1; attempt <= 5; attempt++) {
            authenticateClient.authenticate(caller, email, "Errada123");
        }

        assertThat(authenticateClient.authenticate(caller, email, GOOD_PASSWORD))
                .isInstanceOf(ClientAuthentication.Locked.class);
    }

    // A resposta identica nao basta: sem verificar contra uma forma guardada de
    // descarte, o caminho da conta inexistente volta em menos de um milissegundo
    // enquanto o da conta real paga a verificacao inteira, e o cronometro entrega o
    // cadastro. A margem aqui e de mais de uma ordem de grandeza, medida: uma
    // verificacao custa cerca de 32 ms neste contentor, e a ausencia dela custa
    // menos de um.
    @Test
    @DisplayName("conta inexistente paga a mesma verificacao que a conta real")
    void unknownAccountPaysTheSameVerificationCost() {
        ApplicationCaller caller = application("portal-cronometro");
        String neverRegistered = uniqueEmail();

        long start = System.nanoTime();
        authenticateClient.authenticate(caller, neverRegistered, "Errada123");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis)
                .as("verificacao de descarte no caminho da conta inexistente")
                .isGreaterThan(10L);
    }

    // Se so conta real bloqueasse, o desfecho de bloqueio viraria confirmacao de que
    // o e-mail existe.
    @Test
    @DisplayName("e-mail que nao existe bloqueia igual a conta real")
    void unknownEmailLocksLikeARealAccount() {
        ApplicationCaller caller = application("portal-inexistente");
        String neverRegistered = uniqueEmail();
        for (int attempt = 1; attempt <= 4; attempt++) {
            authenticateClient.authenticate(caller, neverRegistered, "Errada123");
        }

        assertThat(authenticateClient.authenticate(caller, neverRegistered, "Errada123"))
                .isInstanceOf(ClientAuthentication.Locked.class);
    }
}
