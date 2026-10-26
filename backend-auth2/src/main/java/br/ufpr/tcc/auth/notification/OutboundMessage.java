package br.ufpr.tcc.auth.notification;

/**
 * Mensagem que sai para o endereco cadastrado de uma pessoa.
 *
 * O segredo viaja neste objeto e **nunca** em log: quem registra a mensagem registra que
 * ela saiu, para quem e com que proposito, e nada alem disso.
 */
public record OutboundMessage(String recipient, Purpose purpose, String token) {

    public enum Purpose { EMAIL_CONFIRMATION, PASSWORD_RECOVERY }
}
