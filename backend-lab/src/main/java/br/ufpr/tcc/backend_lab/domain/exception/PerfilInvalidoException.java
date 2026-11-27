package br.ufpr.tcc.backend_lab.domain.exception;

public class PerfilInvalidoException extends RuntimeException {
    public PerfilInvalidoException(String message) {
        super(message);
    }
}
