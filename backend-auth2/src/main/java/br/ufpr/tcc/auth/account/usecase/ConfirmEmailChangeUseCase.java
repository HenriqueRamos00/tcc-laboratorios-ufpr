package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.AccountConstraintViolation;
import br.ufpr.tcc.auth.account.ClientAccount;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.account.ClientEmailChange;
import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirma um dos dois enderecos da troca de e-mail; com os dois confirmados, o e-mail muda.
 *
 * Rota: POST /api/self-service/email-change/confirm, credencial de aplicacao. O link vale uma
 * vez so e apenas na aplicacao da conta. A troca e desfeita, e os links morrem, quando o
 * pedido venceu, quando a credencial mudou depois do pedido ou quando outra conta tomou o
 * endereco novo. Concluir a troca sobe a versao da credencial. A transacao e aberta pelo
 * modelo para que a violacao da restricao de e-mail seja traduzida depois do rollback.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ConfirmEmailChangeUseCase {

    public enum Outcome {
        AWAITING_OTHER_CONFIRMATION, COMPLETED, INVALID_TOKEN,
        EMAIL_ALREADY_IN_USE, EMAIL_CHANGE_CONFLICT
    }

    private final ClientAccountStore store;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    public Outcome execute(ApplicationCaller caller, String presentedToken) {
        Optional<ConfirmationToken> parsed = ConfirmationToken.fromPresentedValue(presentedToken);
        if (parsed.isEmpty()) {
            return Outcome.INVALID_TOKEN;
        }
        Optional<ClientAccountStore.EmailChangeTokenOwner> owner =
                store.findEmailChangeTokenOwner(caller.applicationId(), parsed.get());
        if (owner.isEmpty()) {
            return Outcome.INVALID_TOKEN;
        }
        try {
            return transactions.execute(status -> {
                long accountId = owner.get().accountId();
                Optional<ClientAccount> found = store.lockedAccount(caller.applicationId(), accountId);
                if (found.isEmpty() || !found.get().active() || !found.get().emailConfirmed()) {
                    return Outcome.INVALID_TOKEN;
                }
                ClientAccount account = found.get();
                if (!store.consumeEmailChangeToken(parsed.get(), owner.get().purpose())) {
                    return Outcome.INVALID_TOKEN;
                }
                Optional<ClientEmailChange> pending =
                        store.pendingEmailChange(accountId, caller.applicationId());
                OffsetDateTime now = OffsetDateTime.now();
                if (pending.isEmpty() || !pending.get().expiresAt().isAfter(now)) {
                    store.discardPendingEmailChange(accountId);
                    return Outcome.EMAIL_CHANGE_CONFLICT;
                }
                ClientEmailChange emailChange = pending.get();
                if (emailChange.credentialVersion() != account.credentialVersion()) {
                    store.discardPendingEmailChange(accountId);
                    return Outcome.EMAIL_CHANGE_CONFLICT;
                }
                if (store.emailUsedByAnotherIn(caller.applicationId(), emailChange.newEmail(),
                        accountId)) {
                    store.discardPendingEmailChange(accountId);
                    return Outcome.EMAIL_ALREADY_IN_USE;
                }
                if (owner.get().purpose() == OutboundMessage.Purpose.EMAIL_CHANGE_CURRENT) {
                    emailChange.confirmCurrentEmail();
                } else {
                    emailChange.confirmNewEmail();
                }
                if (!emailChange.currentEmailConfirmed() || !emailChange.newEmailConfirmed()) {
                    store.saveEmailChange(emailChange);
                    auditTrail.clientCredentialChanged("email_change_address_confirmed",
                            Long.toString(accountId));
                    return Outcome.AWAITING_OTHER_CONFIRMATION;
                }
                account.changeEmail(emailChange.newEmail());
                store.saveAccount(account);
                store.invalidatePendingEmailChangeTokens(accountId);
                emailChange.clearPending();
                store.saveEmailChange(emailChange);
                auditTrail.clientCredentialChanged("email_changed", Long.toString(accountId));
                return Outcome.COMPLETED;
            });
        } catch (DataIntegrityViolationException failure) {
            if (!AccountConstraintViolation.matches(failure, "UK_CLIENT_ACCOUNT_EMAIL")) {
                throw failure;
            }
            transactions.executeWithoutResult(status ->
                    store.discardPendingEmailChange(owner.get().accountId()));
            return Outcome.EMAIL_ALREADY_IN_USE;
        }
    }
}
