package br.ufpr.tcc.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * Dois pedidos simultaneos de recuperacao de senha para a mesma conta.
 *
 * A regra diz que o pedido novo mata o anterior: usado, vencido ou substituido, o link
 * anterior morre. O codigo implementa isso invalidando antes de inserir, e no isolamento de
 * leitura confirmada duas transacoes nao se veem inserir: as duas invalidam o que estava
 * confirmado, nenhuma enxerga a linha recem inserida da outra, e as duas inserem. O desfecho
 * sao dois links de recuperacao validos ao mesmo tempo, onde a regra promete um.
 *
 * Isso importa mais aqui que no consumo do link, que ja tem caso de corrida proprio:
 * recuperacao de senha existe justamente para a conta que pode estar comprometida, e manter
 * vivo um link que o pedido da vitima deveria ter matado e o oposto do que ela pediu.
 *
 * O invariante agora vive no banco, num indice unico parcial, e nao na ordem dos comandos.
 */
@TestPropertySource(properties = "auth.notification.record-messages=true")
class ConcurrentRecoveryRequestIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(993_000L);

    @Autowired
    private AccountLifecycle lifecycle;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("dois pedidos simultaneos de recuperacao deixam um unico link vivo")
    void twoSimultaneousRecoveryRequestsLeaveOnlyOneLivingLink() throws Exception {
        long applicationId = NEXT.incrementAndGet();
        String code = "corrida-recuperacao-" + applicationId;
        jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .param("id", applicationId).param("code", code)
                .param("now", OffsetDateTime.now()).update();
        ApplicationCaller caller = new ApplicationCaller(applicationId, code);
        String email = "corrida" + NEXT.incrementAndGet() + "@exemplo.com";
        lifecycle.register(caller, Long.toString(applicationId), "Pessoa", email,
                "SenhaForte123");

        int threads = 4;
        CyclicBarrier allAtOnce = new CyclicBarrier(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int worker = 0; worker < threads; worker++) {
                results.add(pool.submit(() -> {
                    allAtOnce.await();
                    lifecycle.requestPasswordRecovery(caller, email);
                    return true;
                }));
            }
        }
        for (Future<Boolean> result : results) {
            result.get();
        }

        assertThat(livingLinksOf(email, "PASSWORD_RECOVERY"))
                .as("quantos links de recuperacao continuam valendo").isOne();
    }

    private long livingLinksOf(String email, String purpose) {
        return jdbcClient.sql("SELECT COUNT(*) FROM ACCOUNT_CONFIRMATION confirmation"
                        + " JOIN CLIENT_ACCOUNT account"
                        + " ON account.ID = confirmation.CLIENT_ACCOUNT_ID"
                        + " WHERE account.EMAIL_NORMALIZED = LOWER(:email)"
                        + " AND confirmation.PURPOSE = :purpose"
                        + " AND confirmation.USED_AT IS NULL")
                .param("email", email).param("purpose", purpose)
                .query(Long.class).single();
    }
}
