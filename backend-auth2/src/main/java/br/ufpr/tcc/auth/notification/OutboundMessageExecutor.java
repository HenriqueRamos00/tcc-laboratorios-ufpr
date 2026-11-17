package br.ufpr.tcc.auth.notification;

import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Pool exclusivo do envio de mensagem, com fila limitada. */
@Component
class OutboundMessageExecutor implements DisposableBean {

    private final ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();

    OutboundMessageExecutor(
            @Value("${auth.notification.sender-threads}") int senderThreads,
            @Value("${auth.notification.queue-capacity}") int queueCapacity,
            @Value("${auth.notification.shutdown-wait}") Duration shutdownWait) {
        pool.setThreadNamePrefix("outbound-message-");
        pool.setCorePoolSize(senderThreads);
        pool.setMaxPoolSize(senderThreads);
        pool.setQueueCapacity(queueCapacity);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(Math.toIntExact(shutdownWait.toSeconds()));
        pool.initialize();
    }

    void execute(Runnable task) {
        pool.execute(task);
    }

    @Override
    public void destroy() {
        pool.shutdown();
    }
}
