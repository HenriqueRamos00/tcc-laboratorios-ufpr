package br.ufpr.tcc.backend_auth.domain.exception;

public class LoginBloqueadoException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginBloqueadoException(long retryAfterSeconds) {
        super("Limite de tentativas atingido. Tente novamente após o período de espera");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
