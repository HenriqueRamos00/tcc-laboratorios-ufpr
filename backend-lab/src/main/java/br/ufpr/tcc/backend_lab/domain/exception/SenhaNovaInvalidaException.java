package br.ufpr.tcc.backend_lab.domain.exception;

public class SenhaNovaInvalidaException extends RuntimeException {
    public SenhaNovaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
