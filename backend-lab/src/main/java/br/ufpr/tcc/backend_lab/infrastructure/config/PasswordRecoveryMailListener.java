package br.ufpr.tcc.backend_lab.infrastructure.config;

import br.ufpr.tcc.backend_lab.application.event.PasswordRecoveryEmailRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "auth.recovery", name = "mail-enabled", havingValue = "true")
public class PasswordRecoveryMailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryMailListener.class);

    private final JavaMailSender mailSender;
    private final String sender;

    public PasswordRecoveryMailListener(
            JavaMailSender mailSender,
            @Value("${auth.recovery.sender}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PasswordRecoveryEmailRequested event) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(sender);
        mail.setTo(event.recipient());
        mail.setSubject("Redefinição de senha - Portal Lactec");
        mail.setText("Recebemos uma solicitação para redefinir a senha da sua conta.\n\n"
                + "Acesse o link abaixo para escolher uma nova senha. Ele expira em uma hora e só pode ser usado uma vez.\n\n"
                + event.recoveryLink()
                + "\n\nSe você não solicitou a redefinição, ignore este e-mail.");
        try {
            mailSender.send(mail);
        } catch (MailException failure) {
            LOGGER.warn("Não foi possível enviar o e-mail de recuperação: {}",
                    failure.getClass().getSimpleName());
        }
    }
}
