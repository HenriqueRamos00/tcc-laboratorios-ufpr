package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
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

/**
 * Dois cadastros simultaneos do mesmo CNPJ na mesma aplicacao.
 *
 * A consulta de CNPJ repetido acontece antes da gravacao, e entre as duas existe janela: as
 * duas chamadas passam pela consulta e a segunda bate na restricao unica do banco. Quem
 * recusa de fato e a restricao, e sem traduzir essa recusa o desfecho e erro de servidor
 * onde a matriz de codigos manda conflito.
 *
 * O caso sequencial nao exercita isso, porque nele a consulta previa ja encontra a empresa e
 * responde conflito sem chegar ao banco. Esta classe existe pelo mesmo motivo que a irma de
 * cadastro de conta: a janela estava ainda mais aberta aqui, porque a consulta morava no
 * controlador, fora da transacao da gravacao.
 */
class ConcurrentCompanyRegistrationIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(994_000L);

    @Autowired
    private CompanyRegistry registry;

    @Autowired
    private DatabaseProbe database;

    @Test
    @DisplayName("dois cadastros simultaneos do mesmo CNPJ: um cria, o outro responde conflito")
    void twoSimultaneousRegistrationsOfTheSameCnpj() throws Exception {
        long applicationId = NEXT.incrementAndGet();
        String code = "corrida-empresa-" + applicationId;
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", applicationId).with("code", code)
                .with("now", OffsetDateTime.now()).run();
        String cnpj = String.format("%014d", NEXT.incrementAndGet());

        int threads = 2;
        CyclicBarrier allAtOnce = new CyclicBarrier(threads);
        List<Future<CompanyRegistry.Registration>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int worker = 0; worker < threads; worker++) {
                results.add(pool.submit(() -> {
                    allAtOnce.await();
                    return registry.register(applicationId, "Empresa Exemplo", cnpj);
                }));
            }
        }

        List<CompanyRegistry.Registration.Outcome> outcomes = new ArrayList<>();
        for (Future<CompanyRegistry.Registration> result : results) {
            outcomes.add(result.get().outcome());
        }

        assertThat(outcomes).as("nenhuma das duas pode estourar, e as duas nao podem criar")
                .containsExactlyInAnyOrder(CompanyRegistry.Registration.Outcome.CREATED,
                        CompanyRegistry.Registration.Outcome.CNPJ_ALREADY_REGISTERED);
        assertThat(companiesWith(applicationId, cnpj)).isOne();
    }

    private long companiesWith(long applicationId, String cnpj) {
        return database.statement("SELECT COUNT(*) FROM COMPANY"
                        + " WHERE APPLICATION_ID = :applicationId AND CNPJ = :cnpj")
                .with("applicationId", applicationId).with("cnpj", cnpj)
                .count();
    }
}
