package br.ufpr.tcc.backend_lab.domain.exception;

import java.time.Duration;

public class LimiteAlteracaoEmailException extends RuntimeException {
    private final Duration tempoRestante;

    public LimiteAlteracaoEmailException(Duration tempoRestante) {
        super("Aguarde antes de solicitar outra alteração de e-mail.");
        this.tempoRestante = tempoRestante;
    }

    public Duration getTempoRestante() {
        return tempoRestante;
    }
}
