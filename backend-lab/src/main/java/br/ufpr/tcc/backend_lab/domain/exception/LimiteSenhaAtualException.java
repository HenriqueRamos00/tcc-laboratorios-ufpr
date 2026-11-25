package br.ufpr.tcc.backend_lab.domain.exception;

import java.time.Duration;

public class LimiteSenhaAtualException extends RuntimeException {
    private final Duration tempoRestante;

    public LimiteSenhaAtualException(Duration tempoRestante) {
        super("Aguarde antes de confirmar a senha atual novamente.");
        this.tempoRestante = tempoRestante;
    }

    public Duration getTempoRestante() {
        return tempoRestante;
    }
}
