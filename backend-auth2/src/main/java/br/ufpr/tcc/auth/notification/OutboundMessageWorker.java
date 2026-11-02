package br.ufpr.tcc.auth.notification;

import br.ufpr.tcc.auth.shared.observability.MdcKeys;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tira o envio do caminho da resposta e decide o que acontece quando ele falha.
 *
 * O ouvinte e de depois do commit, e nao um ouvinte comum, porque mensagem publicada antes do
 * commit sairia com um token cuja linha ainda nao existe para ninguem: o link chegaria antes do
 * registro dele e o primeiro clique seria recusado. Transacao desfeita nao gera mensagem
 * nenhuma, que e o desfecho certo e nao um efeito colateral.
 *
 * A submissao ao executor e escrita a mao, e nao com uma anotacao de execucao assincrona nesta
 * assinatura, e o motivo esta no contrato das duas coisas. Excecao que escapa de um ouvinte de
 * depois do commit sobe pela chamada do commit, ou seja, pela thread da requisicao; e a
 * anotacao em metodo sem retorno submete na hora, deixando a recusa do executor subir para quem
 * chamou. Juntos, os dois transformariam fila cheia em erro de servidor num cadastro que ja
 * esta gravado. Com a submissao a mao, a recusa e tratada aqui e a requisicao nem sabe que
 * existiu.
 *
 * Nada do que e registrado aqui carrega endereco, nome ou token, e por isso tambem nao se
 * registra a pilha nem o texto da excecao: o texto de uma falha de envio costuma trazer o
 * endereco recusado. Quem precisa ligar a linha do envio a conta usa o identificador de
 * correlacao, que a requisicao ja deixou na trilha de auditoria.
 */
@Component
class OutboundMessageWorker {

    /** Contagem acumulada desde a subida, publicada pela sonda de saude. */
    record Counters(long delivered, long failed, long dropped, long shortCircuited,
            long withoutTransport) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboundMessageWorker.class);

    private final Optional<MessageTransport> transport;

    private final OutboundMessageExecutor executor;

    private final MailDeliveryCircuit circuit;

    private final AtomicLong delivered = new AtomicLong();

    private final AtomicLong failed = new AtomicLong();

    private final AtomicLong dropped = new AtomicLong();

    private final AtomicLong shortCircuited = new AtomicLong();

    private final AtomicLong withoutTransport = new AtomicLong();

    OutboundMessageWorker(Optional<MessageTransport> transport, OutboundMessageExecutor executor,
            MailDeliveryCircuit circuit) {
        this.transport = transport;
        this.executor = executor;
        this.circuit = circuit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    void deliverAfterCommit(OutboundMessageReady ready) {
        try {
            executor.execute(() -> sendWithCorrelation(ready));
        } catch (RejectedExecutionException queueIsFull) {
            dropped.incrementAndGet();
            LOGGER.warn("Mensagem de {} descartada: a fila de envio esta cheia",
                    ready.message().purpose());
        }
    }

    Counters counters() {
        return new Counters(delivered.get(), failed.get(), dropped.get(), shortCircuited.get(),
                withoutTransport.get());
    }

    boolean hasTransport() {
        return transport.isPresent();
    }

    private void sendWithCorrelation(OutboundMessageReady ready) {
        if (ready.correlationId() != null) {
            MdcKeys.publish(MdcKeys.CORRELATION_ID, ready.correlationId());
        }
        try {
            send(ready.message());
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }

    private void send(OutboundMessage message) {
        if (transport.isEmpty()) {
            withoutTransport.incrementAndGet();
            LOGGER.warn("Mensagem de {} descartada: nenhum servidor de e-mail configurado",
                    message.purpose());
            return;
        }
        if (!circuit.allowsAttempt()) {
            shortCircuited.incrementAndGet();
            LOGGER.warn("Mensagem de {} descartada: o disjuntor do envio esta aberto",
                    message.purpose());
            return;
        }
        try {
            transport.get().send(message);
            circuit.recordSuccess();
            delivered.incrementAndGet();
            LOGGER.info("Mensagem de {} entregue ao servidor de e-mail", message.purpose());
        } catch (MessageDeliveryFailedException failure) {
            circuit.recordFailure();
            failed.incrementAndGet();
            LOGGER.warn("Mensagem de {} nao entregue, motivo {}", message.purpose(),
                    failure.reason());
        } catch (RuntimeException unexpected) {
            circuit.recordFailure();
            failed.incrementAndGet();
            LOGGER.warn("Mensagem de {} nao entregue, falha inesperada do tipo {}",
                    message.purpose(), unexpected.getClass().getSimpleName());
        }
    }
}
