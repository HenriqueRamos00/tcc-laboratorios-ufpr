package br.ufpr.tcc.auth.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Prova a regra das cinco tentativas contra um Oracle de verdade.
 *
 * Sem banco real nao da para provar nada disto: o que impede a contagem de se perder e o
 * incremento acontecer dentro do comando, e o que impede a corrida entre consultar e agir
 * e a condicao viver no mesmo WHERE.
 */
class LoginAttemptLedgerIntegrationTest extends OracleIntegrationTestBase {

    private static final Duration COOLDOWN = Duration.ofMinutes(15);

    /**
     * Espera de duracao zero, que vence no instante em que e gravada.
     *
     * O caminho do bloqueio vencido precisa ser exercitado sem relogio de parede e sem
     * adiantar a coluna por fora, que seria o teste reimplementando o que a producao faz.
     * Com espera zero o proprio banco grava um prazo que o comando seguinte ja ve no
     * passado, e o valor-limite fica exato.
     */
    private static final Duration ALREADY_EXPIRED = Duration.ZERO;

    @Autowired
    private LoginAttemptLedger ledger;

    private String uniqueIdentifier() {
        return UUID.randomUUID() + "@exemplo.com";
    }

    // Valor-limite da regra: a quarta falha ainda deixa tentar, a quinta bloqueia, e a
    // sexta nem chega a verificar senha.
    @Test
    @DisplayName("a quinta falha bloqueia, e a quarta ainda nao")
    void fifthFailureLocksAndFourthDoesNot() {
        String identifier = uniqueIdentifier();

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(ledger.registerFailure(1L, identifier, COOLDOWN).locked()).isFalse();
        }
        assertThat(ledger.registerFailure(1L, identifier, COOLDOWN).locked())
                .as("quarta falha").isFalse();
        assertThat(ledger.registerFailure(1L, identifier, COOLDOWN).locked())
                .as("quinta falha").isTrue();
    }

    @Test
    @DisplayName("depois de bloqueado, a tentativa seguinte nao e nem avaliada")
    void attemptAfterLockIsNotEvaluated() {
        String identifier = uniqueIdentifier();
        for (int attempt = 1; attempt <= 5; attempt++) {
            ledger.registerFailure(1L, identifier, COOLDOWN);
        }

        LoginAttemptOutcome fifth = ledger.currentOutcome(1L, identifier);
        LoginAttemptOutcome sixth = ledger.registerFailure(1L, identifier, COOLDOWN);

        assertThat(sixth.locked()).isTrue();
        assertThat(sixth.retryAfterSeconds()).isBetween(1L, COOLDOWN.toSeconds());
        assertThat(ledger.failedAttemptsOf(1L, identifier))
                .as("a tentativa depois do bloqueio nao e debitada").isEqualTo(5);
        assertThat(sixth.retryAfterSeconds())
                .as("e nao renova o prazo, senao bater na rota em laco prenderia a conta")
                .isLessThanOrEqualTo(fifth.retryAfterSeconds());
    }

    /**
     * A regra do Lactec diz cinco tentativas e espera configuravel, nao cinco tentativas na
     * vida. Vencida a espera, a conta volta a ter as cinco: sem recomecar a contagem, a
     * primeira falha depois do bloqueio leva o contador a seis, o limite e visto como
     * estourado outra vez, e tranca pelo periodo inteiro. Isso transforma uma requisicao por
     * periodo, em vez de cinco, na alavanca para manter conta alheia fechada.
     */
    @Test
    @DisplayName("vencida a espera, a contagem recomeca e a conta volta a ter cinco tentativas")
    void countRestartsWhenTheWaitExpires() {
        String identifier = uniqueIdentifier();
        for (int attempt = 1; attempt <= 5; attempt++) {
            ledger.registerFailure(4L, identifier, ALREADY_EXPIRED);
        }

        LoginAttemptOutcome firstAfterTheWait = ledger.registerFailure(4L, identifier, COOLDOWN);

        assertThat(firstAfterTheWait.locked())
                .as("a primeira falha depois da espera nao tranca").isFalse();
        assertThat(ledger.failedAttemptsOf(4L, identifier))
                .as("a contagem recomeca do um").isOne();
        for (int attempt = 2; attempt <= 4; attempt++) {
            assertThat(ledger.registerFailure(4L, identifier, COOLDOWN).locked())
                    .as("tentativa " + attempt + " da rodada nova").isFalse();
        }
        assertThat(ledger.registerFailure(4L, identifier, COOLDOWN).locked())
                .as("a quinta da rodada nova tranca de novo").isTrue();
    }

    // Sucesso zera o contador: conta pouco usada que erra uma vez por mes nao pode
    // acumular ate trancar.
    @Test
    @DisplayName("sucesso zera a contagem")
    void successClearsTheCount() {
        String identifier = uniqueIdentifier();
        for (int attempt = 1; attempt <= 4; attempt++) {
            ledger.registerFailure(1L, identifier, COOLDOWN);
        }

        ledger.registerSuccess(1L, identifier);

        assertThat(ledger.registerFailure(1L, identifier, COOLDOWN).locked()).isFalse();
    }

    // O identificador que nao existe no cadastro conta igual ao que existe. Sem
    // isto, o 429 viraria confirmacao de que a conta existe.
    @Test
    @DisplayName("identificador desconhecido bloqueia igual a conta existente")
    void unknownIdentifierLocksLikeAnExistingAccount() {
        String neverRegistered = uniqueIdentifier();

        for (int attempt = 1; attempt <= 4; attempt++) {
            ledger.registerFailure(1L, neverRegistered, COOLDOWN);
        }

        assertThat(ledger.registerFailure(1L, neverRegistered, COOLDOWN).locked()).isTrue();
    }

    @Test
    @DisplayName("o mesmo identificador em outra aplicacao tem contagem propria")
    void sameIdentifierInAnotherApplicationCountsSeparately() {
        String identifier = uniqueIdentifier();
        for (int attempt = 1; attempt <= 5; attempt++) {
            ledger.registerFailure(1L, identifier, COOLDOWN);
        }

        assertThat(ledger.registerFailure(2L, identifier, COOLDOWN).locked()).isFalse();
    }

    // Leitura seguida de escrita perde contagem: as duas transacoes leem tres e
    // gravam quatro. O incremento dentro do comando nao perde, porque o segundo
    // escritor espera a trava da linha e reaplica sobre o valor confirmado.
    @Test
    @DisplayName("falhas simultaneas nao perdem contagem")
    void concurrentFailuresDoNotLoseCount() throws Exception {
        String identifier = uniqueIdentifier();
        int threads = 5;
        CyclicBarrier allAtOnce = new CyclicBarrier(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int worker = 0; worker < threads; worker++) {
                results.add(pool.submit(() -> {
                    allAtOnce.await();
                    return ledger.registerFailure(3L, identifier, COOLDOWN).locked();
                }));
            }
            for (Future<Boolean> result : results) {
                result.get();
            }
        }

        assertThat(ledger.failedAttemptsOf(3L, identifier)).isEqualTo(5);
    }
}
