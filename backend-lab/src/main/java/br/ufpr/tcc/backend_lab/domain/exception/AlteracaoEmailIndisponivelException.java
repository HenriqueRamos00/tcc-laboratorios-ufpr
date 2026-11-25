package br.ufpr.tcc.backend_lab.domain.exception;

public class AlteracaoEmailIndisponivelException extends RuntimeException {
    public AlteracaoEmailIndisponivelException() {
        super("A alteração de e-mail não está disponível no momento.");
    }
}
