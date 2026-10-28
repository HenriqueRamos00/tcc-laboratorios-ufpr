package br.ufpr.tcc.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Dois cadastros simultaneos do mesmo endereco na mesma aplicacao.
 *
 * A consulta de endereco repetido acontece antes da gravacao, e entre as duas existe janela:
 * as duas chamadas passam pela consulta e a segunda bate na restricao unica do banco. Quem
 * recusa de fato e a restricao, e sem traduzir essa recusa o desfecho e erro de servidor onde
 * a matriz de codigos manda conflito.
 *
 * O caso sequencial nao exercita isso, porque nele a consulta previa ja encontra a conta e
 * responde conflito sem chegar ao banco.
 */
class ConcurrentAccountRegistrationIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(996_000L);

    @Autowired
    private AccountLifecycle lifecycle;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("dois cadastros simultaneos do mesmo endereco: um cria, o outro responde conflito")
    void twoSimultaneousRegistrationsOfTheSameAddress() throws Exception {
        long applicationId = NEXT.incrementAndGet();
        String code = "corrida-cadastro-" + applicationId;
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", applicationId).param("code", code)
                .param("now", OffsetDateTime.now()).update();
        ApplicationCaller caller = new ApplicationCaller(applicationId, code);
        String email = "corrida" + NEXT.incrementAndGet() + "@exemplo.com";

        int threads = 2;
        CyclicBarrier allAtOnce = new CyclicBarrier(threads);
        List<Future<AccountLifecycle.RegistrationOutcome>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int worker = 0; worker < threads; worker++) {
                results.add(pool.submit(() -> {
                    allAtOnce.await();
                    return lifecycle.register(caller, Long.toString(applicationId), "Pessoa",
                            email, "SenhaForte123");
                }));
            }
        }

        List<AccountLifecycle.RegistrationOutcome> outcomes = new ArrayList<>();
        for (Future<AccountLifecycle.RegistrationOutcome> result : results) {
            outcomes.add(result.get());
        }

        assertThat(outcomes).as("nenhuma das duas pode estourar, e as duas nao podem criar")
                .containsExactlyInAnyOrder(AccountLifecycle.RegistrationOutcome.CREATED,
                        AccountLifecycle.RegistrationOutcome.EMAIL_TAKEN);
        assertThat(accountsWith(applicationId, email)).isEqualTo(1L);
    }

    private long accountsWith(long applicationId, String email) {
        return jdbcClient.sql("SELECT COUNT(*) FROM CLIENT_ACCOUNT"
                        + " WHERE APPLICATION_ID = :applicationId"
                        + " AND EMAIL_NORMALIZED = LOWER(:email)")
                .param("applicationId", applicationId).param("email", email)
                .query(Long.class).single();
    }
}
