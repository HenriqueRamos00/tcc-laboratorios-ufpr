package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccount;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.account.ClientEmailChange;
import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.EmailTransportAvailability;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pede a troca de e-mail pelo autoatendimento, com a senha atual.
 *
 * Rota: POST /api/self-service/accounts/{accountId}/email-change, credencial de aplicacao.
 * Pedido aceito manda um link ao endereco atual e outro ao novo; o e-mail so muda quando os
 * dois forem confirmados, em ConfirmEmailChangeUseCase. Pedido novo substitui o anterior. O
 * limite e de tres pedidos a cada quinze minutos, alem do limite da senha atual.
 */
@Service
public class RequestEmailChangeUseCase {

    public enum Outcome {
        ACCEPTED, EMAIL_DELIVERY_UNAVAILABLE, NOT_FOUND, CURRENT_PASSWORD_INCORRECT,
        EMAIL_ALREADY_IN_USE, EMAIL_CHANGE_CONFLICT, RATE_LIMITED
    }

    /** Desfecho do pedido, com o pedido pendente quando aceito e a espera quando limitado. */
    public record Result(Outcome outcome, PendingEmailChange pending, long retryAfterSeconds) {

        static Result refused(Outcome outcome) {
            return new Result(outcome, null, 0);
        }
    }

    private final ClientAccountStore store;
    private final CurrentPasswordVerification currentPasswordVerification;
    private final MessageDelivery messageDelivery;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;
    private final EmailTransportAvailability emailTransport;
    private final Duration emailChangeLifetime;

    RequestEmailChangeUseCase(ClientAccountStore store,
            CurrentPasswordVerification currentPasswordVerification,
            MessageDelivery messageDelivery, AuditTrail auditTrail,
            TransactionTemplate transactions, EmailTransportAvailability emailTransport,
            @Value("${auth.email-change.lifetime:PT1H}") Duration emailChangeLifetime) {
        this.store = store;
        this.currentPasswordVerification = currentPasswordVerification;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
        this.emailTransport = emailTransport;
        this.emailChangeLifetime = emailChangeLifetime;
    }

    public Result execute(ApplicationCaller caller, String accountId, String newEmail,
            String currentPassword) {
        if (!emailTransport.isEmailChangeAvailable()) {
            return Result.refused(Outcome.EMAIL_DELIVERY_UNAVAILABLE);
        }
        Optional<Long> parsed = Identifiers.parsePositive(accountId);
        if (parsed.isEmpty()) {
            return Result.refused(Outcome.NOT_FOUND);
        }
        String requestedEmail = ClientAccountStore.withoutSurroundingSpace(newEmail);
        ConfirmationToken currentToken = ConfirmationToken.generate();
        ConfirmationToken newToken = ConfirmationToken.generate();
        return transactions.execute(status -> {
            Optional<ClientAccount> found = store.lockedAccount(caller.applicationId(), parsed.get())
                    .filter(account -> account.active() && account.emailConfirmed());
            if (found.isEmpty()) {
                return Result.refused(Outcome.NOT_FOUND);
            }
            ClientAccount account = found.get();
            OffsetDateTime now = OffsetDateTime.now();
            long passwordWait = currentPasswordVerification.secondsUntilNextAttempt(account, now);
            if (passwordWait > 0) {
                return new Result(Outcome.RATE_LIMITED, null, passwordWait);
            }
            ClientEmailChange emailChange = store.emailChange(account.id(), caller.applicationId());
            if (emailChange.requestLimitReached(now)) {
                long waitSeconds = Math.max(1, Duration.between(now,
                        emailChange.windowExpiresAt()).toSeconds() + 1);
                store.saveEmailChange(emailChange);
                return new Result(Outcome.RATE_LIMITED, null, waitSeconds);
            }
            if (!currentPasswordVerification.matches(account, currentPassword, now)) {
                return Result.refused(Outcome.CURRENT_PASSWORD_INCORRECT);
            }
            emailChange.recordRequest(now);
            if (account.email().equalsIgnoreCase(requestedEmail)) {
                store.saveEmailChange(emailChange);
                return Result.refused(Outcome.EMAIL_CHANGE_CONFLICT);
            }
            if (store.emailUsedByAnotherIn(caller.applicationId(), requestedEmail, account.id())) {
                store.saveEmailChange(emailChange);
                return Result.refused(Outcome.EMAIL_ALREADY_IN_USE);
            }

            store.invalidatePendingEmailChangeTokens(account.id());
            emailChange.start(requestedEmail, account.credentialVersion(), now);
            store.saveEmailChange(emailChange);
            store.issueEmailChangeToken(account.id(), currentToken,
                    OutboundMessage.Purpose.EMAIL_CHANGE_CURRENT, emailChangeLifetime);
            store.issueEmailChangeToken(account.id(), newToken,
                    OutboundMessage.Purpose.EMAIL_CHANGE_NEW, emailChangeLifetime);
            messageDelivery.deliver(new OutboundMessage(account.email(),
                    OutboundMessage.Purpose.EMAIL_CHANGE_CURRENT, currentToken.clearValue()));
            messageDelivery.deliver(new OutboundMessage(requestedEmail,
                    OutboundMessage.Purpose.EMAIL_CHANGE_NEW, newToken.clearValue()));
            auditTrail.clientCredentialChanged("email_change_requested", Long.toString(account.id()));
            return new Result(Outcome.ACCEPTED,
                    new PendingEmailChange(requestedEmail, emailChange.expiresAt(), false, false),
                    0);
        });
    }
}
