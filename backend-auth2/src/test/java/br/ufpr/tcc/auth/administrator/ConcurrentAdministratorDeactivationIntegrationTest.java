package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Prova que o servico nunca fica sem administrador ativo, inclusive quando duas requisicoes
 * chegam juntas.
 *
 * O caso sequencial nao exercita a corrida: nele a contagem de ativos ja ve a desativacao
 * anterior confirmada. A janela aparece quando as duas contam antes de qualquer uma gravar, e
 * ficar sem administrador ativo nao tem volta pela API, porque a rota de instalacao conta
 * linha na tabela e a linha desativada continua contando.
 *
 * O entrelacamento e deterministico de proposito, e nao sorteado: a requisicao lenta e a que
 * troca a senha, porque o calculo do hash acontece depois da contagem e custa ordens de
 * grandeza mais que um comando de banco. Enquanto ela calcula, a rapida conta.
 */
class ConcurrentAdministratorDeactivationIntegrationTest extends OracleIntegrationTestBase {

    private static final String PASSWORD = "SenhaForte123";
    private static final String NEW_PASSWORD = "OutraSenhaValida456";
    private static final long FIRST_IDENTIFIER = 940_001L;
    private static final long SECOND_IDENTIFIER = 940_002L;
    private static final String FIRST_EMAIL = "primeiro.da.corrida@lactec.exemplo";
    private static final String SECOND_EMAIL = "segundo.da.corrida@lactec.exemplo";

    @Autowired
    private AdministratorLifecycle lifecycle;

    @Autowired
    private DatabaseProbe database;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedTwoActiveAdministrators() {
        database.statement("DELETE FROM ADMINISTRATIVE_SESSION").run();
        database.statement("DELETE FROM ADMINISTRATOR").run();
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insert(FIRST_IDENTIFIER, FIRST_EMAIL, passwordHash);
        insert(SECOND_IDENTIFIER, SECOND_EMAIL, passwordHash);
    }

    @Test
    @DisplayName("duas desativacoes simultaneas nao deixam o servico sem administrador ativo")
    void twoSimultaneousDeactivationsLeaveAnActiveAdministrator() throws Exception {
        CyclicBarrier allAtOnce = new CyclicBarrier(2);
        Future<AdministratorLifecycle.Change> slow;
        Future<AdministratorLifecycle.Change> quick;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            slow = pool.submit(() -> {
                allAtOnce.await();
                return lifecycle.update(Long.toString(FIRST_IDENTIFIER), "Primeiro", FIRST_EMAIL,
                        false, NEW_PASSWORD);
            });
            quick = pool.submit(() -> {
                allAtOnce.await();
                return lifecycle.update(Long.toString(SECOND_IDENTIFIER), "Segundo", SECOND_EMAIL,
                        false, null);
            });
        }

        boolean firstChanged = slow.get().outcome() == AdministratorLifecycle.Change.Outcome.CHANGED;
        boolean secondChanged =
                quick.get().outcome() == AdministratorLifecycle.Change.Outcome.CHANGED;
        assertThat(firstChanged && secondChanged)
                .as("as duas desativacoes nao podem valer").isFalse();
        assertThat(activeAdministrators())
                .as("administradores ativos depois das duas chamadas").isPositive();
    }

    @Test
    @DisplayName("desativar um e excluir o outro ao mesmo tempo nao esvazia o cadastro ativo")
    void simultaneousDeactivationAndDeletionLeaveAnActiveAdministrator() throws Exception {
        CyclicBarrier allAtOnce = new CyclicBarrier(2);
        Future<AdministratorLifecycle.Change> slow;
        Future<AdministratorLifecycle.Removal> quick;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            slow = pool.submit(() -> {
                allAtOnce.await();
                return lifecycle.update(Long.toString(FIRST_IDENTIFIER), "Primeiro", FIRST_EMAIL,
                        false, NEW_PASSWORD);
            });
            quick = pool.submit(() -> {
                allAtOnce.await();
                return lifecycle.delete(Long.toString(SECOND_IDENTIFIER));
            });
        }

        boolean deactivated = slow.get().outcome() == AdministratorLifecycle.Change.Outcome.CHANGED;
        boolean removed = quick.get() == AdministratorLifecycle.Removal.REMOVED;
        assertThat(deactivated && removed)
                .as("desativar um e excluir o outro nao podem valer as duas").isFalse();
        assertThat(activeAdministrators())
                .as("administradores ativos depois das duas chamadas").isPositive();
    }

    private void insert(long id, String email, String passwordHash) {
        database.statement("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador da corrida', :email, :email, TRUE, :hash,"
                        + " :now, :now)")
                .with("id", id).with("email", email).with("hash", passwordHash)
                .with("now", OffsetDateTime.now()).run();
    }

    private long activeAdministrators() {
        return database.statement("SELECT COUNT(*) FROM ADMINISTRATOR WHERE ACTIVE = TRUE")
                .count();
    }
}
