package br.ufpr.tcc.auth.notification;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Estado observavel do envio de e-mail, sem abrir conexao nenhuma.
 *
 * A sonda de e-mail do proprio Boot fica desligada no application.yaml, e nao por
 * duplicidade. Ela e sonda ativa: consulta o servidor a cada chamada de /health, e /health
 * e rota aberta, entao qualquer um na rede poderia usar a sonda do servico de autenticacao
 * para bater no servidor de e-mail do Lactec. Alem disso ela reporta DOWN quando o servidor
 * de e-mail cai, e DOWN em /health faz a borda tirar de rotacao o unico servico de
 * autenticacao que existe, derrubando o login por causa de um e-mail que nao saiu.
 *
 * Esta sonda e passiva: ela publica o que as tentativas de verdade observaram. Com o
 * disjuntor fechado o estado e UP; aberto, ela devolve OUT_OF_SERVICE, que o
 * application.yaml mapeia para 200 de proposito, porque funcao degradada nao e processo
 * morto e o corpo da resposta ja conta a verdade a quem le.
 *
 * Os detalhes saem so no grupo /health/mail, e nenhum deles identifica pessoa ou servidor:
 * estado, se existe transporte, falhas em sequencia e contagem por desfecho.
 */
@Component
class MailDeliveryHealthIndicator implements HealthIndicator {

    private final MailDeliveryCircuit circuit;

    private final OutboundMessageWorker worker;

    MailDeliveryHealthIndicator(MailDeliveryCircuit circuit, OutboundMessageWorker worker) {
        this.circuit = circuit;
        this.worker = worker;
    }

    @Override
    public Health health() {
        MailDeliveryCircuit.Snapshot snapshot = circuit.snapshot();
        OutboundMessageWorker.Counters counters = worker.counters();
        Health.Builder builder = snapshot.state() == MailDeliveryCircuit.State.OPEN
                ? Health.outOfService()
                : Health.up();
        return builder
                .withDetail("state", snapshot.state().name())
                .withDetail("transport", worker.hasTransport() ? "SMTP" : "ABSENT")
                .withDetail("consecutiveFailures", snapshot.consecutiveFailures())
                .withDetail("changedAt", snapshot.changedAt().toString())
                .withDetail("delivered", counters.delivered())
                .withDetail("failed", counters.failed())
                .withDetail("dropped", counters.dropped())
                .withDetail("shortCircuited", counters.shortCircuited())
                .withDetail("withoutTransport", counters.withoutTransport())
                .build();
    }
}
