package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Autoatendimento da conta, chamado pela aplicacao em nome do cliente que entrou: perfil,
 * troca de senha, troca de e-mail e estado da sessao. Cadastro, leitura, alteracao, exclusao,
 * confirmacao de e-mail e recuperacao de senha estao nos casos de uso de account.usecase.
 */
@Service
public class ClientAccountService {
    private static final int MINIMUM_SELF_SERVICE_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_SELF_SERVICE_PASSWORD_LENGTH = 72;

    private final ClientAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final MessageDelivery messageDelivery;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    @Value("${auth.email-change.lifetime:PT1H}")
    private Duration emailChangeLifetime = Duration.ofHours(1);

    ClientAccountService(ClientAccountStore store, PasswordEncoder passwordEncoder,
            MessageDelivery messageDelivery, AuditTrail auditTrail,
            TransactionTemplate transactions) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
    }

    public record SelfServiceCompany(String id, String cnpj) {
    }

    public record SelfServicePasswordPolicy(int minCodePoints, int maxCharacters,
            Integer maxUtf8Bytes, boolean requiresUppercase, boolean requiresLowercase,
            boolean requiresDigit, boolean rejectsBlank) {
    }

    public record SelfServiceProfile(String name, String email, String role, boolean active,
            boolean emailConfirmed, List<String> editableFields, List<String> actions,
            List<SelfServiceCompany> companies, PendingEmailChange pendingEmailChange,
            SelfServicePasswordPolicy passwordPolicy) {
    }

    public record PendingEmailChange(String newEmail, OffsetDateTime expiresAt,
            boolean currentEmailConfirmed, boolean newEmailConfirmed) {
    }

    public record SessionState(String id, boolean active, boolean emailConfirmed,
            long credentialVersion) {
    }

    public enum SelfServiceOutcome {
        CHANGED, NOT_FOUND, CURRENT_PASSWORD_INCORRECT, PASSWORD_REJECTED, RATE_LIMITED
    }

    public record PasswordChange(SelfServiceOutcome outcome, long retryAfterSeconds) {
        PasswordChange(SelfServiceOutcome outcome) {
            this(outcome, 0);
        }
    }

    public enum EmailChangeOutcome {
        ACCEPTED, NOT_FOUND, CURRENT_PASSWORD_INCORRECT, EMAIL_ALREADY_IN_USE,
        EMAIL_CHANGE_CONFLICT, RATE_LIMITED, INVALID_TOKEN
    }

    public record EmailChangeRequest(EmailChangeOutcome outcome, PendingEmailChange pending,
            long retryAfterSeconds) {
    }

    public enum EmailChangeConfirmationOutcome {
        AWAITING_OTHER_CONFIRMATION, COMPLETED, INVALID_TOKEN,
        EMAIL_ALREADY_IN_USE, EMAIL_CHANGE_CONFLICT
    }

    public Optional<SelfServiceProfile> selfServiceProfile(ApplicationCaller caller,
            String accountId) {
        Optional<ClientAccountStore.SelfServiceAccount> found =
                selfServiceAccount(caller, accountId, false);
        return found.filter(account -> account.active() && account.emailConfirmed())
                .map(account -> profileOf(account, List.of("change-password"),
                        pendingEmailChange(account)));
    }

    public Optional<SelfServiceProfile> updateSelfServiceProfile(ApplicationCaller caller,
            String accountId, String name) {
        Optional<Long> parsed = Identifiers.parsePositive(accountId);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        return transactions.execute(status -> {
            Optional<ClientAccountStore.SelfServiceAccount> found = store.selfServiceAccount(
                    caller.applicationId(), parsed.get(), true);
            if (found.isEmpty() || !found.get().active() || !found.get().emailConfirmed()) {
                return Optional.empty();
            }
            ClientAccount account = store.lockedAccount(caller.applicationId(), parsed.get())
                    .orElseThrow(() -> new IllegalStateException(
                            "Conta autorizada desapareceu durante a transacao"));
            account.changeName(name.trim());
            auditTrail.clientAccountChanged("self_service_profile_changed",
                    caller.applicationCode(), Long.toString(parsed.get()));
            return store.selfServiceAccount(caller.applicationId(), parsed.get(), false)
                    .map(updated -> profileOf(updated, List.of("change-password"),
                            pendingEmailChange(updated)));
        });
    }

    public PasswordChange changeSelfServicePassword(ApplicationCaller caller,
            String accountId, String currentPassword, String newPassword) {
        Optional<Long> parsed = Identifiers.parsePositive(accountId);
        if (parsed.isEmpty()) {
            return new PasswordChange(SelfServiceOutcome.NOT_FOUND);
        }
        if (newPassword == null || newPassword.length() > MAXIMUM_SELF_SERVICE_PASSWORD_LENGTH
                || newPassword.codePointCount(0, newPassword.length()) < MINIMUM_SELF_SERVICE_PASSWORD_LENGTH
                || !PasswordPolicy.violationsOf(newPassword).isEmpty()) {
            return new PasswordChange(SelfServiceOutcome.PASSWORD_REJECTED);
        }
        return transactions.execute(status -> {
            Optional<ClientAccount> found = store.lockedAccount(caller.applicationId(), parsed.get())
                    .filter(account -> account.active() && account.emailConfirmed());
            if (found.isEmpty()) {
                return new PasswordChange(SelfServiceOutcome.NOT_FOUND);
            }
            ClientAccount account = found.get();
            OffsetDateTime now = OffsetDateTime.now();
            long wait = retryAfterSeconds(account.currentPasswordWait(now));
            if (wait > 0) {
                return new PasswordChange(SelfServiceOutcome.RATE_LIMITED, wait);
            }
            if (!verifyCurrentPassword(account, currentPassword, now)) {
                return new PasswordChange(SelfServiceOutcome.CURRENT_PASSWORD_INCORRECT);
            }
            account.changePasswordHash(passwordEncoder.encode(newPassword));
            store.invalidatePendingTokens(parsed.get(), OutboundMessage.Purpose.PASSWORD_RECOVERY);
            store.invalidatePendingEmailChangeTokens(parsed.get());
            store.clearEmailChange(parsed.get());
            auditTrail.clientCredentialChanged("password_changed", Long.toString(parsed.get()));
            return new PasswordChange(SelfServiceOutcome.CHANGED);
        });
    }

    public EmailChangeRequest requestEmailChange(ApplicationCaller caller, String accountId,
            String newEmail, String currentPassword) {
        Optional<Long> parsed = Identifiers.parsePositive(accountId);
        if (parsed.isEmpty()) {
            return new EmailChangeRequest(EmailChangeOutcome.NOT_FOUND, null, 0);
        }
        String requestedEmail = ClientAccountStore.withoutSurroundingSpace(newEmail);
        ConfirmationToken currentToken = ConfirmationToken.generate();
        ConfirmationToken newToken = ConfirmationToken.generate();
        return transactions.execute(status -> {
            Optional<ClientAccount> found = store.lockedAccount(caller.applicationId(), parsed.get())
                    .filter(account -> account.active() && account.emailConfirmed());
            if (found.isEmpty()) {
                return new EmailChangeRequest(EmailChangeOutcome.NOT_FOUND, null, 0);
            }
            ClientAccount account = found.get();
            OffsetDateTime now = OffsetDateTime.now();
            long passwordWait = retryAfterSeconds(account.currentPasswordWait(now));
            if (passwordWait > 0) {
                return new EmailChangeRequest(EmailChangeOutcome.RATE_LIMITED, null, passwordWait);
            }
            ClientEmailChange emailChange = store.emailChange(account.id(), caller.applicationId());
            if (emailChange.requestLimitReached(now)) {
                long waitSeconds = Math.max(1, Duration.between(now,
                        emailChange.windowExpiresAt()).toSeconds() + 1);
                store.saveEmailChange(emailChange);
                return new EmailChangeRequest(EmailChangeOutcome.RATE_LIMITED, null, waitSeconds);
            }
            if (!verifyCurrentPassword(account, currentPassword, now)) {
                return new EmailChangeRequest(
                        EmailChangeOutcome.CURRENT_PASSWORD_INCORRECT, null, 0);
            }
            emailChange.recordRequest(now);
            if (account.email().equalsIgnoreCase(requestedEmail)) {
                store.saveEmailChange(emailChange);
                return new EmailChangeRequest(EmailChangeOutcome.EMAIL_CHANGE_CONFLICT, null, 0);
            }
            if (store.emailUsedByAnotherIn(caller.applicationId(), requestedEmail, account.id())) {
                store.saveEmailChange(emailChange);
                return new EmailChangeRequest(
                        EmailChangeOutcome.EMAIL_ALREADY_IN_USE, null, 0);
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
            return new EmailChangeRequest(EmailChangeOutcome.ACCEPTED,
                    new PendingEmailChange(requestedEmail, emailChange.expiresAt(),
                            false, false), 0);
        });
    }

    private boolean verifyCurrentPassword(ClientAccount account, String currentPassword,
            OffsetDateTime now) {
        if (passwordEncoder.matches(currentPassword, account.passwordHash())) {
            account.clearCurrentPasswordFailures();
            return true;
        }
        account.recordCurrentPasswordFailure(now);
        return false;
    }

    private static long retryAfterSeconds(Duration wait) {
        return wait.toSeconds() + (wait.getNano() == 0 ? 0 : 1);
    }

    public boolean cancelEmailChange(ApplicationCaller caller, String accountId) {
        Optional<Long> parsed = Identifiers.parsePositive(accountId);
        if (parsed.isEmpty()) {
            return false;
        }
        return transactions.execute(status -> {
            Optional<ClientAccountStore.SelfServiceAccount> found = store.selfServiceAccount(
                    caller.applicationId(), parsed.get(), true);
            if (found.isEmpty()) {
                return false;
            }
            ClientAccount account = store.lockedAccount(caller.applicationId(), parsed.get())
                    .orElseThrow(() -> new IllegalStateException(
                            "Conta autorizada desapareceu durante a transacao"));
            store.invalidatePendingEmailChangeTokens(account.id());
            store.clearEmailChange(account.id());
            auditTrail.clientCredentialChanged("email_change_cancelled", Long.toString(account.id()));
            return true;
        });
    }

    public EmailChangeConfirmationOutcome confirmEmailChange(ApplicationCaller caller,
            String presentedToken) {
        Optional<ConfirmationToken> parsed = ConfirmationToken.fromPresentedValue(presentedToken);
        if (parsed.isEmpty()) {
            return EmailChangeConfirmationOutcome.INVALID_TOKEN;
        }
        Optional<ClientAccountStore.EmailChangeTokenOwner> owner =
                store.findEmailChangeTokenOwner(caller.applicationId(), parsed.get());
        if (owner.isEmpty()) {
            return EmailChangeConfirmationOutcome.INVALID_TOKEN;
        }
        try {
            return transactions.execute(status -> {
                long accountId = owner.get().accountId();
                Optional<ClientAccount> found = store.lockedAccount(caller.applicationId(), accountId);
                if (found.isEmpty() || !found.get().active() || !found.get().emailConfirmed()) {
                    return EmailChangeConfirmationOutcome.INVALID_TOKEN;
                }
                ClientAccount account = found.get();
                if (!store.consumeEmailChangeToken(parsed.get(), owner.get().purpose())) {
                    return EmailChangeConfirmationOutcome.INVALID_TOKEN;
                }
                Optional<ClientEmailChange> pending =
                        store.pendingEmailChange(accountId, caller.applicationId());
                OffsetDateTime now = OffsetDateTime.now();
                if (pending.isEmpty() || !pending.get().expiresAt().isAfter(now)) {
                    clearPendingEmailChange(accountId);
                    return EmailChangeConfirmationOutcome.EMAIL_CHANGE_CONFLICT;
                }
                ClientEmailChange emailChange = pending.get();
                if (emailChange.credentialVersion() != account.credentialVersion()) {
                    clearPendingEmailChange(accountId);
                    return EmailChangeConfirmationOutcome.EMAIL_CHANGE_CONFLICT;
                }
                if (store.emailUsedByAnotherIn(caller.applicationId(), emailChange.newEmail(),
                        accountId)) {
                    clearPendingEmailChange(accountId);
                    return EmailChangeConfirmationOutcome.EMAIL_ALREADY_IN_USE;
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
                    return EmailChangeConfirmationOutcome.AWAITING_OTHER_CONFIRMATION;
                }
                account.changeEmail(emailChange.newEmail());
                store.saveAccount(account);
                store.invalidatePendingEmailChangeTokens(accountId);
                emailChange.clearPending();
                store.saveEmailChange(emailChange);
                auditTrail.clientCredentialChanged("email_changed", Long.toString(accountId));
                return EmailChangeConfirmationOutcome.COMPLETED;
            });
        } catch (DataIntegrityViolationException failure) {
            if (!AccountConstraintViolation.matches(failure, "UK_CLIENT_ACCOUNT_EMAIL")) {
                throw failure;
            }
            transactions.executeWithoutResult(status ->
                    clearPendingEmailChange(owner.get().accountId()));
            return EmailChangeConfirmationOutcome.EMAIL_ALREADY_IN_USE;
        }
    }

    private void clearPendingEmailChange(long accountId) {
        store.invalidatePendingEmailChangeTokens(accountId);
        store.clearEmailChange(accountId);
    }

    public Optional<SessionState> sessionState(ApplicationCaller caller, String accountId) {
        return selfServiceAccount(caller, accountId, false).map(account -> new SessionState(
                Long.toString(account.id()), account.active(), account.emailConfirmed(),
                account.credentialVersion()));
    }

    private Optional<ClientAccountStore.SelfServiceAccount> selfServiceAccount(
            ApplicationCaller caller, String accountId, boolean lockForUpdate) {
        return Identifiers.parsePositive(accountId).flatMap(id ->
                store.selfServiceAccount(caller.applicationId(), id, lockForUpdate));
    }

    private static SelfServiceProfile profileOf(ClientAccountStore.SelfServiceAccount account,
            List<String> actions, PendingEmailChange pendingEmailChange) {
        List<String> companyIds = account.companyIds();
        List<String> cnpjs = account.companyCnpjs();
        List<SelfServiceCompany> companies = new ArrayList<>();
        for (int index = 0; index < companyIds.size(); index++) {
            companies.add(new SelfServiceCompany(companyIds.get(index), cnpjs.get(index)));
        }
        return new SelfServiceProfile(account.name(), account.email(), "CLIENTE",
                account.active(), account.emailConfirmed(), List.of("name"), actions,
                List.copyOf(companies), pendingEmailChange,
                new SelfServicePasswordPolicy(MINIMUM_SELF_SERVICE_PASSWORD_LENGTH,
                        MAXIMUM_SELF_SERVICE_PASSWORD_LENGTH, null, true, true, true, true));
    }

    private PendingEmailChange pendingEmailChange(
            ClientAccountStore.SelfServiceAccount account) {
        OffsetDateTime now = OffsetDateTime.now();
        return store.pendingEmailChange(account.id(), account.applicationId())
                .filter(change -> change.expiresAt().isAfter(now)
                        && change.credentialVersion() == account.credentialVersion())
                .map(change -> new PendingEmailChange(change.newEmail(), change.expiresAt(),
                        change.currentEmailConfirmed(), change.newEmailConfirmed()))
                .orElse(null);
    }
}
