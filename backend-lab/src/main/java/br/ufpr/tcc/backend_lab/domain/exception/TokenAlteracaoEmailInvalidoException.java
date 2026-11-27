package br.ufpr.tcc.backend_lab.domain.exception;

public class TokenAlteracaoEmailInvalidoException extends RuntimeException {
    public TokenAlteracaoEmailInvalidoException() {
        super("O link de alteração de e-mail é inválido ou expirou.");
    }
}
