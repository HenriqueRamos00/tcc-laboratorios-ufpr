package br.ufpr.tcc.auth.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O disjuntor do envio de e-mail, nos dois lados de cada decisao.
 *
 * O relogio entra pelo construtor porque a espera antes da tentativa de prova se mede em
 * minutos, e teste que dorme um minuto nao e rodado por ninguem.
 */
class MailDeliveryCircuitTest {

    /** Relogio que so anda quando o caso manda. */
    private static final class AdvanceableClock extends Clock {

        private Instant now = Instant.parse("2026-10-26T12:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    private final AdvanceableClock clock = new AdvanceableClock();

    @Test
    @DisplayName("fechado tenta sempre")
    void closedCircuitAlwaysAllowsAnAttempt() {
        MailDeliveryCircuit circuit =
                new MailDeliveryCircuit(3, Duration.ofMinutes(1), clock);

        assertThat(circuit.allowsAttempt()).isTrue();
        assertThat(circuit.allowsAttempt()).isTrue();
    }

    /**
     * Valor limite do limiar, nos dois lados: falha isolada e normal numa rede, e abrir antes
     * do limiar descartaria mensagem boa.
     */
    @Test
    @DisplayName("duas falhas mantem o disjuntor fechado e a terceira abre")
    void thresholdOpensOnlyOnTheDeclaredFailure() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(3, Duration.ofMinutes(1), clock);

        circuit.recordFailure();
        circuit.recordFailure();
        assertThat(circuit.allowsAttempt()).as("duas falhas ainda nao abrem").isTrue();

        circuit.recordFailure();
        assertThat(circuit.allowsAttempt()).as("a terceira abre").isFalse();
    }

    @Test
    @DisplayName("sucesso no meio zera a sequencia de falhas")
    void successResetsTheFailureSequence() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(3, Duration.ofMinutes(1), clock);

        circuit.recordFailure();
        circuit.recordFailure();
        circuit.recordSuccess();
        circuit.recordFailure();
        circuit.recordFailure();

        assertThat(circuit.allowsAttempt()).as("quatro falhas com um sucesso no meio nao abrem")
                .isTrue();
    }

    /** Valor limite da espera: um milissegundo antes recusa, na hora exata libera. */
    @Test
    @DisplayName("aberto recusa antes da espera e libera uma tentativa na hora exata")
    void openCircuitReleasesOneProbeWhenTheWaitIsOver() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(1, Duration.ofMinutes(1), clock);
        circuit.recordFailure();

        clock.advance(Duration.ofMinutes(1).minusMillis(1));
        assertThat(circuit.allowsAttempt()).as("um milissegundo antes ainda recusa").isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(circuit.allowsAttempt()).as("na hora exata libera a prova").isTrue();
        assertThat(circuit.allowsAttempt()).as("a prova e uma so").isFalse();
    }

    /**
     * Enquanto a prova esta em curso, nenhuma outra passa, e isso vale mesmo depois de a espera
     * inteira correr de novo: o que libera a proxima e o desfecho da prova, nao o relogio.
     *
     * Este caso nasceu de uma mutacao que sobreviveu. Com o relogio parado, remover o guarda
     * da prova em curso nao mudava nada, porque a espera recem reiniciada ainda nao tinha
     * passado. O defeito so aparece quando o tempo anda, e era exatamente ele que escapava.
     */
    @Test
    @DisplayName("a prova em curso nao e liberada de novo nem depois de a espera correr")
    void probeInFlightIsNotReleasedAgainByTheClock() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(1, Duration.ofMinutes(1), clock);
        circuit.recordFailure();
        clock.advance(Duration.ofMinutes(1));
        assertThat(circuit.allowsAttempt()).as("a primeira prova sai").isTrue();

        clock.advance(Duration.ofMinutes(5));

        assertThat(circuit.allowsAttempt())
                .as("quem libera a proxima e o desfecho da prova, nao o relogio").isFalse();
        assertThat(circuit.snapshot().state()).isEqualTo(MailDeliveryCircuit.State.HALF_OPEN);
    }

    @Test
    @DisplayName("prova que passa fecha o disjuntor")
    void successfulProbeClosesTheCircuit() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(1, Duration.ofMinutes(1), clock);
        circuit.recordFailure();
        clock.advance(Duration.ofMinutes(1));
        circuit.allowsAttempt();

        circuit.recordSuccess();

        assertThat(circuit.allowsAttempt()).isTrue();
        assertThat(circuit.snapshot().state()).isEqualTo(MailDeliveryCircuit.State.CLOSED);
    }

    @Test
    @DisplayName("prova que falha reabre e recomeca a espera")
    void failedProbeReopensAndRestartsTheWait() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(1, Duration.ofMinutes(1), clock);
        circuit.recordFailure();
        clock.advance(Duration.ofMinutes(1));
        circuit.allowsAttempt();

        circuit.recordFailure();

        assertThat(circuit.allowsAttempt()).as("a espera recomecou").isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(circuit.allowsAttempt()).as("passada a nova espera, nova prova").isTrue();
    }

    @Test
    @DisplayName("a sonda publica o estado e a sequencia de falhas, e nada mais")
    void snapshotPublishesStateAndFailureCount() {
        MailDeliveryCircuit circuit = new MailDeliveryCircuit(2, Duration.ofMinutes(1), clock);

        circuit.recordFailure();

        MailDeliveryCircuit.Snapshot snapshot = circuit.snapshot();
        assertThat(snapshot.state()).isEqualTo(MailDeliveryCircuit.State.CLOSED);
        assertThat(snapshot.consecutiveFailures()).isEqualTo(1);
        assertThat(snapshot.changedAt()).isNotNull();
    }

    /** Fronteira de construtor: configuracao sem sentido nao vira disjuntor que nao protege. */
    @Test
    @DisplayName("limiar zero e espera zero sao recusados na construcao")
    void meaninglessConfigurationIsRefused() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new MailDeliveryCircuit(0, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new MailDeliveryCircuit(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
