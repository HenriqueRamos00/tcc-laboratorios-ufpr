package br.ufpr.tcc.backend_auth.domain.exception;

public class ContaNaoEncontradaException extends RuntimeException {
    public ContaNaoEncontradaException() {
        super("Conta não encontrada");
    }
}
