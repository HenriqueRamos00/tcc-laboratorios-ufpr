package br.ufpr.tcc.backend_lab.domain.exception;

public class EmailJaUtilizadoException extends RuntimeException {
    public EmailJaUtilizadoException() {
        super("O e-mail informado já está cadastrado.");
    }
}
