package br.ufpr.tcc.backend_auth.domain.exception;

public class ConflitoServicoException extends RuntimeException {
    public ConflitoServicoException(String message) {
        super(message);
    }
}
