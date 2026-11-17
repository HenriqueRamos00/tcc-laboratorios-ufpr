package br.ufpr.tcc.auth.notification;

/**
 * A mensagem nao chegou ao servidor de e-mail.
 *
 * Ela nao guarda a causa original e nao guarda o endereco, e isso e decisao e nao
 * esquecimento: o texto da falha de envio publica os enderecos recusados, entao qualquer
 * ponto que registrasse esta excecao com a pilha levaria o endereco de uma pessoa para o log.
 * O que atravessa a porta e o motivo classificado mais o nome da classe da falha original,
 * que bastam para diagnosticar e nao identificam ninguem.
 */
public final class MessageDeliveryFailedException extends RuntimeException {

    /** Como a tentativa terminou, no vocabulario de quem opera o servico. */
    public enum Reason {
        TIMEOUT, CONNECTION_REFUSED, ENCRYPTION_REFUSED, AUTHENTICATION_REFUSED, SERVER_REFUSED,
        UNEXPECTED
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public MessageDeliveryFailedException(Reason reason, String originalFailureType) {
        super(reason.name() + " (" + originalFailureType + ")");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
