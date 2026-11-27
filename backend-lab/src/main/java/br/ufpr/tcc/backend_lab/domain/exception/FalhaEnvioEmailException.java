package br.ufpr.tcc.backend_lab.domain.exception;

public class FalhaEnvioEmailException extends RuntimeException {
    public FalhaEnvioEmailException() {
        super("Não foi possível enviar as mensagens de confirmação. Tente novamente.");
    }
}
