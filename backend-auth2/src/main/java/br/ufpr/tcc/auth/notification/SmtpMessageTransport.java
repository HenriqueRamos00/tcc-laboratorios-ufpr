package br.ufpr.tcc.auth.notification;

import br.ufpr.tcc.auth.notification.MessageDeliveryFailedException.Reason;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adaptador de servidor de e-mail, falando SMTP pelo JavaMailSender.
 *
 * Ele existe somente quando spring.mail.host esta definido, que e a mesma condicao da
 * configuracao automatica do JavaMailSender. Ambiente sem servidor de e-mail nao ganha um
 * remetente de mentira: ele fica sem transporte, e cada mensagem descartada aparece no log
 * e na contagem da sonda. O contrario, um adaptador que escolhesse entre enviar e registrar
 * por propriedade, seria o interruptor que alguem liga em producao sem perceber que parou
 * de mandar e-mail.
 *
 * O endereco do link vem de propriedade sem valor padrao, e o construtor recusa um molde
 * sem o marcador do token. Essa e a falha que ninguem percebe em teste e ninguem perdoa em
 * producao: a mensagem sai, o texto parece certo, e o link nao confirma nada.
 *
 * A traducao da falha e o motivo de send ter try. O que a biblioteca lanca carrega o
 * endereco recusado dentro do texto, entao a excecao que atravessa a porta leva o motivo
 * classificado e o nome da classe original, e nada mais.
 */
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
class SmtpMessageTransport implements MessageTransport {

    private static final String TOKEN_PLACEHOLDER = "{token}";

    private final JavaMailSender mailSender;

    private final String sender;

    private final String confirmationLink;

    private final String recoveryLink;

    SmtpMessageTransport(JavaMailSender mailSender,
            @Value("${auth.notification.sender}") String sender,
            @Value("${auth.notification.confirmation-link}") String confirmationLink,
            @Value("${auth.notification.recovery-link}") String recoveryLink) {
        this.mailSender = mailSender;
        this.sender = sender;
        this.confirmationLink = requirePlaceholder(confirmationLink, "confirmation-link");
        this.recoveryLink = requirePlaceholder(recoveryLink, "recovery-link");
    }

    private static String requirePlaceholder(String template, String propertyName) {
        if (!template.contains(TOKEN_PLACEHOLDER)) {
            throw new IllegalStateException("A propriedade auth.notification." + propertyName
                    + " precisa conter o marcador " + TOKEN_PLACEHOLDER);
        }
        return template;
    }

    @Override
    public void send(OutboundMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(sender);
        mail.setTo(message.recipient());
        mail.setSubject(subjectOf(message.purpose()));
        mail.setText(bodyOf(message));
        try {
            mailSender.send(mail);
        } catch (MailAuthenticationException refused) {
            throw new MessageDeliveryFailedException(Reason.AUTHENTICATION_REFUSED,
                    refused.getClass().getSimpleName());
        } catch (MailException failure) {
            throw new MessageDeliveryFailedException(reasonOf(failure), deepestTypeOf(failure));
        }
    }

    /**
     * Classifica a falha pelo tipo, e a cifra recusada tambem pelo texto.
     *
     * A cifra e a unica que nao tem tipo proprio na biblioteca: servidor que nao anuncia
     * STARTTLS com a exigencia ligada vira uma falha de mensagem generica, e sem este caso ela
     * sairia classificada como inesperada, mandando quem opera procurar no lugar errado. A
     * leitura do texto acontece aqui dentro, onde a excecao ainda existe, e o texto nao
     * atravessa a porta: o que sai e o motivo.
     */
    private static Reason reasonOf(MailException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return Reason.TIMEOUT;
            }
            if (mentionsEncryption(cause.getMessage())) {
                return Reason.ENCRYPTION_REFUSED;
            }
            if (cause instanceof ConnectException) {
                return Reason.CONNECTION_REFUSED;
            }
            if (cause instanceof AuthenticationFailedException) {
                return Reason.AUTHENTICATION_REFUSED;
            }
            if (cause instanceof SendFailedException) {
                return Reason.SERVER_REFUSED;
            }
        }
        return Reason.UNEXPECTED;
    }

    private static boolean mentionsEncryption(String failureText) {
        return failureText != null && failureText.toUpperCase(Locale.ROOT).contains("STARTTLS");
    }

    private static String deepestTypeOf(Throwable failure) {
        Throwable deepest = failure;
        while (deepest.getCause() != null) {
            deepest = deepest.getCause();
        }
        return deepest.getClass().getSimpleName();
    }

    private static String subjectOf(OutboundMessage.Purpose purpose) {
        return switch (purpose) {
            case EMAIL_CONFIRMATION -> "Confirme seu endereco de e-mail";
            case PASSWORD_RECOVERY -> "Recuperacao de senha";
        };
    }

    /**
     * O corpo da mensagem, com o link do proposito e o token dentro dele.
     *
     * Ele e visivel no pacote porque ler o corpo pelo outro lado exigiria servidor de e-mail,
     * e a montagem do link e a decisao que precisa de prova: molde trocado entre propositos
     * manda a pessoa para a tela errada, e token ausente faz a mensagem sair bonita sem
     * confirmar nada.
     */
    String bodyOf(OutboundMessage message) {
        String link = templateOf(message.purpose()).replace(TOKEN_PLACEHOLDER, message.token());
        return switch (message.purpose()) {
            case EMAIL_CONFIRMATION -> """
                    Abra o endereco abaixo para confirmar seu cadastro no Portal Lactec.
                    Ele vale uma vez e por tempo limitado.

                    %s

                    Se nao foi voce quem pediu o cadastro, ignore esta mensagem.
                    """.formatted(link);
            case PASSWORD_RECOVERY -> """
                    Abra o endereco abaixo para cadastrar uma senha nova.
                    Ele vale uma vez e por tempo limitado.

                    %s

                    Se nao foi voce quem pediu, ignore esta mensagem: a senha atual
                    continua valendo.
                    """.formatted(link);
        };
    }

    private String templateOf(OutboundMessage.Purpose purpose) {
        return switch (purpose) {
            case EMAIL_CONFIRMATION -> confirmationLink;
            case PASSWORD_RECOVERY -> recoveryLink;
        };
    }
}
