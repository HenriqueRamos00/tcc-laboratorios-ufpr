package br.ufpr.tcc.backend_lab.domain.exception;

public class ConflitoAlteracaoEmailException extends RuntimeException {
    public ConflitoAlteracaoEmailException() {
        super("A solicitação de alteração de e-mail não está mais válida.");
    }
}
