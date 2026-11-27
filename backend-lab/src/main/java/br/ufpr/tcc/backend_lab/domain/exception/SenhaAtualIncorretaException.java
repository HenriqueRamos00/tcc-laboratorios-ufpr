package br.ufpr.tcc.backend_lab.domain.exception;

public class SenhaAtualIncorretaException extends RuntimeException {
    public SenhaAtualIncorretaException() {
        super("A senha atual está incorreta");
    }
}
