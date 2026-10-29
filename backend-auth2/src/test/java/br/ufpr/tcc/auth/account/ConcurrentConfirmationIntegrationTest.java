package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.notification.RecordedMessages;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Prova que o link de confirmacao e de uso unico tambem quando duas chamadas chegam
 * juntas.
 *
 * Esta classe existe porque a mutacao que remove o guarda de uso do proprio comando
 * **sobreviveu** ao teste sequencial: ali a consulta anterior ja filtra o link usado e a
 * segunda chamada nem chega a gravar. O guarda so e exercitado em corrida, e sem este caso
 * ele seria codigo que nenhum teste protege.
 */
@TestPropertySource(properties = "auth.notification.record-messages=true")
class ConcurrentConfirmationIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT_ID = new AtomicLong(500_000L);

    @Autowired
    private AccountLifecycle lifecycle;

    @Autowired
    private RecordedMessages recordedMessages;

    @Autowired
    private DatabaseProbe database;

    @Test
    @DisplayName("duas confirmacoes simultaneas do mesmo link, so uma vence")
    void twoSimultaneousConfirmationsOfTheSameLinkLeaveOnlyOneWinner() throws Exception {
        long applicationId = NEXT_ID.incrementAndGet();
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", applicationId).with("code", "corrida-" + applicationId)
                .with("now", OffsetDateTime.now()).run();
        String email = "pessoa" + NEXT_ID.incrementAndGet() + "@exemplo.com";
        ApplicationCaller caller = new ApplicationCaller(applicationId,
                "corrida-" + applicationId);
        lifecycle.register(caller, Long.toString(applicationId), "Pessoa", email,
                "SenhaForte123");
        String link = recordedMessages.lastTokenFor(email);

        int threads = 4;
        CyclicBarrier allAtOnce = new CyclicBarrier(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int worker = 0; worker < threads; worker++) {
                results.add(pool.submit(() -> {
                    allAtOnce.await();
                    return lifecycle.confirmEmail(caller, link);
                }));
            }
        }

        long winners = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                winners++;
            }
        }
        assertThat(winners).as("quantas chamadas simultaneas consideraram o link valido").isOne();
    }
}
