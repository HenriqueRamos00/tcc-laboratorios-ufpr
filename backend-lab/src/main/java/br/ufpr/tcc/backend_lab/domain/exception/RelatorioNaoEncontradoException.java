package br.ufpr.tcc.backend_lab.domain.exception;

public class RelatorioNaoEncontradoException extends RuntimeException {
    public RelatorioNaoEncontradoException(String message) {
        super(message);
    }
}
