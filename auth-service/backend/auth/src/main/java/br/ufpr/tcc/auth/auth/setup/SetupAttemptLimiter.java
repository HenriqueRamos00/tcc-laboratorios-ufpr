package br.ufpr.tcc.auth.auth.setup;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SetupAttemptLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Window> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String client) {
        Window window = attempts.get(client);
        if (window == null) return;
        if (window.startedAt.plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(client, window);
            return;
        }
        if (window.count >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de configuração. Tente novamente mais tarde.");
        }
    }

    public void recordFailure(String client) {
        attempts.compute(client, (key, current) -> {
            Instant now = Instant.now();
            if (current == null || current.startedAt.plus(WINDOW).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt, current.count + 1);
        });
    }

    public void clear(String client) {
        attempts.remove(client);
    }

    private record Window(Instant startedAt, int count) {
    }
}
