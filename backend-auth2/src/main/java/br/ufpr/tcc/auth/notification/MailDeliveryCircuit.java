package br.ufpr.tcc.auth.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Disjuntor de tres estados em volta do servidor de e-mail.
 *
 * Ele existe porque a fila de envio e limitada e as threads que a consomem sao duas. Com o
 * servidor fora do ar e sem disjuntor, cada mensagem ocupa uma dessas threads pelo tempo
 * limite inteiro, a fila enche, e o desfecho e o mesmo descarte, depois de gastar minutos de
 * thread e centenas de conexoes condenadas. Aberto, o descarte e imediato, cada mensagem rende
 * a sua propria linha de log, e o servico para de bater na porta de um provedor que responde a
 * excesso de tentativa punindo a reputacao do dominio de quem tentou.
 *
 * Fechado tenta sempre. Aberto recusa tudo ate a espera passar e entao libera uma tentativa de
 * prova, uma so: o objetivo da prova e descobrir se o servidor voltou, nao retomar o trafego
 * antes de saber. Prova que passa fecha o disjuntor; prova que falha reabre e recomeca a
 * espera.
 *
 * Os metodos sao sincronizados e nao usam contador atomico porque cada transicao olha tres
 * campos juntos, e contador sozinho deixaria duas threads passarem pela mesma prova.
 *
 * O relogio entra pelo construtor porque o teste precisa adiantar a espera sem dormir.
 */
final class MailDeliveryCircuit {

    enum State { CLOSED, OPEN, HALF_OPEN }

    /** O que a sonda de saude publica. Nao carrega endereco, token nem nome de servidor. */
    record Snapshot(State state, int consecutiveFailures, Instant changedAt) {
    }

    private final int failureThreshold;

    private final Duration waitBeforeProbe;

    private final Clock clock;

    private State state = State.CLOSED;

    private int consecutiveFailures;

    private Instant changedAt;

    MailDeliveryCircuit(int failureThreshold, Duration waitBeforeProbe, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "O limite de falhas em sequencia e no minimo uma falha");
        }
        if (waitBeforeProbe.isZero() || waitBeforeProbe.isNegative()) {
            throw new IllegalArgumentException(
                    "A espera antes da tentativa de prova precisa ser positiva");
        }
        this.failureThreshold = failureThreshold;
        this.waitBeforeProbe = waitBeforeProbe;
        this.clock = clock;
        this.changedAt = clock.instant();
    }

    synchronized boolean allowsAttempt() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.HALF_OPEN) {
            return false;
        }
        if (clock.instant().isBefore(changedAt.plus(waitBeforeProbe))) {
            return false;
        }
        state = State.HALF_OPEN;
        changedAt = clock.instant();
        return true;
    }

    synchronized void recordSuccess() {
        consecutiveFailures = 0;
        if (state != State.CLOSED) {
            state = State.CLOSED;
            changedAt = clock.instant();
        }
    }

    synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            changedAt = clock.instant();
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(state, consecutiveFailures, changedAt);
    }
}
