package br.ufpr.tcc.auth.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import br.ufpr.tcc.auth.shared.observability.MdcKeys;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * O trabalhador de envio visto pelo lado que importa: o que acontece quando da errado.
 *
 * As quatro situacoes que decidem este codigo nenhum servidor de e-mail de verdade produz
 * quando o teste pede, e por isso elas sao exercitadas por um duble na porta de transporte:
 * falha de entrega, fila cheia, disjuntor aberto e transporte ausente.
 */
class OutboundMessageWorkerTest {

    private static final String TOKEN = "cnf_tokenDeTesteQueNaoPodeAparecerNoLogDeJeitoNenhum";
    private static final String RECIPIENT = "pessoa@lactec.exemplo";

    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;
    private OutboundMessageExecutor executor;

    @BeforeEach
    void captureLogEvents() {
        logger = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(OutboundMessageWorker.class);
        captured.start();
        logger.addAppender(captured);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void releaseLogEvents() {
        logger.detachAppender(captured);
        captured.stop();
        MDC.clear();
        if (executor != null) {
            executor.destroy();
        }
    }

    /** Transporte que conta as tentativas e decide, por caso, se entrega ou falha. */
    private static final class TransportDouble implements MessageTransport {

        private final boolean fails;
        private final CountDownLatch finished;
        private final List<String> seenCorrelationIds = new ArrayList<>();
        private int attempts;

        private TransportDouble(boolean fails, int expectedMessages) {
            this.fails = fails;
            this.finished = new CountDownLatch(expectedMessages);
        }

        @Override
        public synchronized void send(OutboundMessage message) {
            attempts++;
            seenCorrelationIds.add(String.valueOf(MDC.get(MdcKeys.CORRELATION_ID)));
            finished.countDown();
            if (fails) {
                throw new MessageDeliveryFailedException(
                        MessageDeliveryFailedException.Reason.TIMEOUT, "SocketTimeoutException");
            }
        }

        private void awaitMessages() throws InterruptedException {
            assertThat(finished.await(5, TimeUnit.SECONDS))
                    .as("o envio precisa terminar dentro do tempo do caso").isTrue();
        }
    }

    private OutboundMessageWorker workerWith(Optional<MessageTransport> transport,
            int failureThreshold, int senderThreads, int queueCapacity) {
        executor = new OutboundMessageExecutor(senderThreads, queueCapacity, Duration.ofSeconds(5));
        return new OutboundMessageWorker(transport, executor,
                new MailDeliveryCircuit(failureThreshold, Duration.ofMinutes(1),
                        Clock.systemUTC()));
    }

    private static OutboundMessageReady readyMessage(String correlationId) {
        return new OutboundMessageReady(new OutboundMessage(RECIPIENT,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, TOKEN), correlationId);
    }

    /**
     * O caso obrigatorio da regra de confirmacao por e-mail: falha de envio e registrada e nao
     * chega a quem chamou. Quem chamou e o commit da transacao do cadastro, e excecao que sobe
     * por ali transformaria um cadastro gravado em erro de servidor.
     */
    @Test
    @DisplayName("falha de envio e registrada e nao chega a quem chamou")
    void deliveryFailureIsRecordedAndNothingReachesTheCaller() throws Exception {
        TransportDouble transport = new TransportDouble(true, 1);
        OutboundMessageWorker worker = workerWith(Optional.of(transport), 3, 1, 10);

        assertThatNoException()
                .isThrownBy(() -> worker.deliverAfterCommit(readyMessage("requisicao-00000001")));
        transport.awaitMessages();
        executor.destroy();

        assertThat(worker.counters().failed()).isEqualTo(1L);
        assertThat(worker.counters().delivered()).isZero();
        assertThat(captured.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("nao entregue").contains("TIMEOUT");
        });
    }

    @Test
    @DisplayName("nenhuma linha de log carrega o token nem o endereco")
    void noLogLineCarriesTheTokenOrTheAddress() throws Exception {
        TransportDouble failing = new TransportDouble(true, 1);
        OutboundMessageWorker failingWorker = workerWith(Optional.of(failing), 3, 1, 10);
        failingWorker.deliverAfterCommit(readyMessage("requisicao-00000002"));
        failing.awaitMessages();
        executor.destroy();

        TransportDouble delivering = new TransportDouble(false, 1);
        OutboundMessageWorker deliveringWorker = workerWith(Optional.of(delivering), 3, 1, 10);
        deliveringWorker.deliverAfterCommit(readyMessage("requisicao-00000003"));
        delivering.awaitMessages();
        executor.destroy();

        assertThat(captured.list).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(TOKEN).doesNotContain(RECIPIENT);
            assertThat(Arrays.toString(event.getArgumentArray()))
                    .doesNotContain(TOKEN).doesNotContain(RECIPIENT);
        });
    }

    /**
     * O identificador de correlacao e copiado no evento porque o MDC nao atravessa thread. A
     * segunda mensagem na mesma thread de envio prova as duas metades de uma vez: que o valor
     * chega, e que ele nao fica grudado para a mensagem seguinte.
     */
    @Test
    @DisplayName("a correlacao atravessa a thread e nao vaza para a mensagem seguinte")
    void correlationIdCrossesTheThreadAndDoesNotLeakToTheNextMessage() throws Exception {
        TransportDouble transport = new TransportDouble(false, 2);
        OutboundMessageWorker worker = workerWith(Optional.of(transport), 3, 1, 10);

        worker.deliverAfterCommit(readyMessage("requisicao-00000004"));
        worker.deliverAfterCommit(readyMessage(null));
        transport.awaitMessages();
        executor.destroy();

        assertThat(transport.seenCorrelationIds).containsExactly("requisicao-00000004", "null");
    }

    @Test
    @DisplayName("fila cheia descarta a mensagem com registro, e sem excecao")
    void fullQueueDropsTheMessageWithoutThrowing() throws Exception {
        CountDownLatch holdTheOnlyThread = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        MessageTransport blocked = message -> {
            started.countDown();
            try {
                holdTheOnlyThread.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        OutboundMessageWorker worker = workerWith(Optional.of(blocked), 3, 1, 1);

        worker.deliverAfterCommit(readyMessage("requisicao-00000005"));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        worker.deliverAfterCommit(readyMessage("requisicao-00000006"));
        assertThatNoException()
                .isThrownBy(() -> worker.deliverAfterCommit(readyMessage("requisicao-00000007")));
        holdTheOnlyThread.countDown();

        assertThat(worker.counters().dropped()).isEqualTo(1L);
        assertThat(captured.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("fila de envio esta cheia"));
    }

    @Test
    @DisplayName("disjuntor aberto para de encostar no transporte")
    void openCircuitStopsTouchingTheTransport() throws Exception {
        TransportDouble transport = new TransportDouble(true, 1);
        OutboundMessageWorker worker = workerWith(Optional.of(transport), 1, 1, 10);

        worker.deliverAfterCommit(readyMessage("requisicao-00000008"));
        transport.awaitMessages();
        worker.deliverAfterCommit(readyMessage("requisicao-00000009"));
        executor.destroy();

        assertThat(transport.attempts).as("a segunda mensagem nao encosta no transporte")
                .isEqualTo(1);
        assertThat(worker.counters().shortCircuited()).isEqualTo(1L);
    }

    @Test
    @DisplayName("sem transporte configurado, a mensagem e descartada com registro")
    void withoutTransportTheMessageIsDroppedWithALogLine() {
        OutboundMessageWorker worker = workerWith(Optional.empty(), 3, 1, 10);

        worker.deliverAfterCommit(readyMessage("requisicao-00000010"));
        executor.destroy();

        assertThat(worker.counters().withoutTransport()).isEqualTo(1L);
        assertThat(worker.hasTransport()).isFalse();
        assertThat(captured.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("nenhum servidor de e-mail configurado"));
    }
}
