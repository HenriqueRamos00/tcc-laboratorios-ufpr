package br.ufpr.tcc.auth.notification;

import br.ufpr.tcc.auth.shared.observability.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Implementacao da porta de envio que nao envia: ela anota a intencao e sai do caminho.
 *
 * As duas coisas que acontecem aqui sao baratas e sincronas de proposito. A copia em memoria
 * serve ao ambiente sem servidor de e-mail e continua desligada por padrao. A publicacao do
 * evento precisa acontecer dentro da transacao do caso de uso, senao o ouvinte de depois do
 * commit nao e chamado. O envio em si nao esta aqui, e sao dois motivos, os dois da regra de
 * confirmacao por e-mail.
 *
 * O primeiro e que falha do servidor de e-mail nao pode derrubar o cadastro. Enviar dentro da
 * transacao poe uma dependencia externa dentro do limite transacional, e a excecao dela desfaz
 * a conta que acabou de nascer.
 *
 * O segundo e que o envio nao pode segurar a resposta. A rota de recuperacao responde igual
 * para endereco que existe e para endereco que nao existe, mas com o envio no caminho da
 * resposta o endereco cadastrado custa o tempo do servidor de e-mail a mais, e o relogio do
 * chamador passa a contar o que o corpo esconde.
 *
 * Pedido fora de transacao e registrado em vez de passar em silencio. Ouvinte de depois do
 * commit nao roda sem transacao, por especificacao, e sem esta linha a mensagem desapareceria
 * sem deixar rastro nenhum.
 */
@Component
class OutboundMessageDispatcher implements MessageDelivery {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboundMessageDispatcher.class);

    private final RecordedMessages recordedMessages;

    private final ApplicationEventPublisher events;

    OutboundMessageDispatcher(RecordedMessages recordedMessages,
            ApplicationEventPublisher events) {
        this.recordedMessages = recordedMessages;
        this.events = events;
    }

    @Override
    public void deliver(OutboundMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            LOGGER.warn("Mensagem de {} pedida fora de transacao e por isso nao vai sair",
                    message.purpose());
            return;
        }
        recordedMessages.record(message);
        events.publishEvent(new OutboundMessageReady(message, MDC.get(MdcKeys.CORRELATION_ID)));
    }
}
