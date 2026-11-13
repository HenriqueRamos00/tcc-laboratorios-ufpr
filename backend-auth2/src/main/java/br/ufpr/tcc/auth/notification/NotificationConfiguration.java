package br.ufpr.tcc.auth.notification;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fios do envio de mensagem que nao cabem numa anotacao. */
@Configuration
class NotificationConfiguration {

    /**
     * O disjuntor nasce aqui, e nao como componente anotado, porque o relogio dele e
     * dependencia de verdade: o teste adianta a espera em vez de dormir, e um componente que
     * chamasse o relogio do sistema por dentro obrigaria a suite a esperar um minuto de relogio
     * de parede para provar a tentativa de prova.
     */
    @Bean
    MailDeliveryCircuit mailDeliveryCircuit(
            @Value("${auth.notification.circuit.failure-threshold}") int failureThreshold,
            @Value("${auth.notification.circuit.wait-before-probe}") Duration waitBeforeProbe) {
        return new MailDeliveryCircuit(failureThreshold, waitBeforeProbe, Clock.systemUTC());
    }
}
