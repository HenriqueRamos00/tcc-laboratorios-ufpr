package br.ufpr.tcc.backend_auth.domain.exception;

public class DadosInvalidosException extends RuntimeException {
    public DadosInvalidosException(String message) {
        super(message);
    }
}
