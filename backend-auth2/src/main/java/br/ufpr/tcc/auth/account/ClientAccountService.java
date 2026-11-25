package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Coordena cadastro, consulta, alteracao, exclusao, confirmacao de e-mail e recuperacao de
 * senha. A escrita de contas e administrativa; confirmacao e recuperacao pertencem a
 * aplicacao. Conflitos esperados sao tratados depois do rollback pelo TransactionTemplate.
 */
@Service
public class ClientAccountService {
    private static final int MINIMUM_SELF_SERVICE_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_SELF_SERVICE_PASSWORD_LENGTH = 72;

    /** Desfecho do cadastro, com a conta criada quando houve criacao. */
    public record Registration(Outcome outcome, AccountSummary account) {

        public enum Outcome {
            CREATED, PASSWORD_REJECTED, EMAIL_TAKEN, SERVICE_NOT_FOUND, SERVICE_INACTIVE,
            COMPANY_NOT_FOUND
        }

        static Registration refused(Outcome outcome) {
            return new Registration(outcome, null);
        }
    }

    /** Desfecho da alteracao, com a conta como ficou quando houve alteracao. */
    public record Change(Outcome outcome, AccountSummary account) {

        public enum Outcome { CHANGED, NOT_FOUND, COMPANY_NOT_FOUND }

        static Change refused(Outcome outcome) {
            return new Change(outcome, null);
        }
    }

    private final ClientAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final MessageDelivery messageDelivery;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;
    private final Duration confirmationLifetime;
    private final Duration recoveryLifetime;

    @Value("${auth.email-change.lifetime:PT1H}")
    private Duration emailChangeLifetime = Duration.ofHours(1);

    ClientAccountService(ClientAccountStore store, PasswordEncoder passwordEncoder,
            MessageDelivery messageDelivery, AuditTrail auditTrail,
            TransactionTemplate transactions,
            @Value("${auth.confirmation.lifetime}") Duration confirmationLifetime,
            @Value("${auth.recovery.lifetime}") Duration recoveryLifetime) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
        this.confirmationLifetime = confirmationLifetime;
        this.recoveryLifetime = recoveryLifetime;
    }

    /**
     * Valida e calcula o hash da senha antes de abrir a transacao. Remove empresas repetidas e
     * desfaz o cadastro se alguma referencia for invalida.
     */
    public Registration register(long applicationId, String name, String email, String password,
            List<Long> companyIds) {
        if (!PasswordPolicy.violationsOf(password).isEmpty()) {
            return Registration.refused(Registration.Outcome.PASSWORD_REJECTED);
        }
        String passwordHash = passwordEncoder.encode(password);
        ConfirmationToken token = ConfirmationToken.generate();
        Set<Long> companies = new LinkedHashSet<>(companyIds);
        try {
            return transactions.execute(status -> {
                Registration registration =
                        registerInTransaction(applicationId, name, email, passwordHash, token, companies);
                if (registration.outcome() != Registration.Outcome.CREATED) {
                    status.setRollbackOnly();
                }
                return registration;
            });
        } catch (DataIntegrityViolationException failure) {
            if (AccountConstraintViolation.matches(failure, "UK_CLIENT_ACCOUNT_EMAIL")) {
                return Registration.refused(Registration.Outcome.EMAIL_TAKEN);
            }
            throw failure;
        }
    }

    /**
     * Executa o cadastro na transacao. A restricao unica garante exclusividade do e-mail mesmo com
     * pedidos simultaneos. Os vinculos sao validados antes de emitir o token e solicitar a
     * mensagem.
     */
    private Registration registerInTransaction(long applicationId, String name, String email, String passwordHash,
            ConfirmationToken token, Set<Long> companyIds) {
        Optional<Boolean> applicationActive = store.applicationSituation(applicationId);
        if (applicationActive.isEmpty()) {
            return Registration.refused(Registration.Outcome.SERVICE_NOT_FOUND);
        }
        if (!applicationActive.get()) {
            return Registration.refused(Registration.Outcome.SERVICE_INACTIVE);
        }
        if (store.emailAlreadyUsedIn(applicationId, email)) {
            return Registration.refused(Registration.Outcome.EMAIL_TAKEN);
        }
        long accountId = store.insert(applicationId, name, email, passwordHash);
        if (!store.replaceCompanies(accountId, companyIds)) {
            return Registration.refused(Registration.Outcome.COMPANY_NOT_FOUND);
        }
        store.issueToken(accountId, token,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, confirmationLifetime);
        messageDelivery.deliver(new OutboundMessage(email,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, token.clearValue()));
        auditTrail.clientAccountChanged("created", Long.toString(applicationId),
                Long.toString(accountId));
        return store.findWithinScope(Optional.empty(), accountId)
                .map(created -> new Registration(Registration.Outcome.CREATED,
                        summaryOf(created)))
                .orElseThrow(() -> new IllegalStateException(
                        "Conta recem gravada nao foi encontrada na mesma transacao"));
    }

    /** As empresas que a conta representa, na forma que o consumidor do login ja conhece. */
    public record Authorizations(List<String> companyCnpjs) {
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

    /** O que as rotas de cadastro e de leitura devolvem sobre uma conta. */
    public record AccountSummary(String id, String name, String email, String role, boolean active,
            String serviceId, boolean emailConfirmed, List<String> companyIds,
            Authorizations authorizations) {
    }

    private static AccountSummary summaryOf(ClientAccountStore.AccountView view) {
        return new AccountSummary(view.id(), view.name(), view.email(), view.role(), view.active(),
                view.serviceId(), view.emailConfirmed(), view.companyIds(),
                new Authorizations(view.companyCnpjs()));
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
        Optional<Long> parsed = positiveIdentifierOf(accountId);
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
        Optional<Long> parsed = positiveIdentifierOf(accountId);
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
        Optional<Long> parsed = positiveIdentifierOf(accountId);
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
        Optional<Long> parsed = positiveIdentifierOf(accountId);
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
        return positiveIdentifierOf(accountId).flatMap(id ->
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

    private static Optional<Long> positiveIdentifierOf(String value) {
        return identifierOf(value).filter(id -> id > 0);
    }

    public List<AccountSummary> list(Optional<Long> applicationScope) {
        return store.list(applicationScope).stream().map(ClientAccountService::summaryOf).toList();
    }

    public Optional<AccountSummary> find(Optional<Long> applicationScope, String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> store.findWithinScope(applicationScope, id))
                .map(ClientAccountService::summaryOf);
    }

    /**
     * Altera nome, situacao e substitui as empresas. Empresa inexistente desfaz toda a operacao. A
     * resposta usa os valores pedidos porque o UPDATE direto nao atualiza a entidade em memoria.
     */
    public Change update(Optional<Long> applicationScope, String accountId, String name,
            boolean active, List<Long> companyIds) {
        Optional<Long> parsed = identifierOf(accountId);
        if (parsed.isEmpty()) {
            return Change.refused(Change.Outcome.NOT_FOUND);
        }
        long id = parsed.get();
        Set<Long> companies = new LinkedHashSet<>(companyIds);
        return transactions.execute(status -> {
            Optional<ClientAccountStore.AccountView> stored =
                    store.findWithinScope(applicationScope, id);
            if (stored.isEmpty()) {
                return Change.refused(Change.Outcome.NOT_FOUND);
            }
            ClientAccountStore.AccountView found = stored.get();
            store.update(applicationScope, id, name, active);
            if (!store.replaceCompanies(id, companies)) {
                status.setRollbackOnly();
                return Change.refused(Change.Outcome.COMPANY_NOT_FOUND);
            }
            auditTrail.clientAccountChanged("changed", found.serviceId(), found.id());
            ClientAccountStore.CompanyLinks linked = store.companiesOf(id);
            return new Change(Change.Outcome.CHANGED,
                    summaryOf(new ClientAccountStore.AccountView(found.id(), name,
                            found.email(), found.role(), active, found.serviceId(),
                            found.emailConfirmed(), linked.companyIds(),
                            linked.companyCnpjs())));
        });
    }

    @Transactional
    public boolean delete(Optional<Long> applicationScope, String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> store.findWithinScope(applicationScope, id).map(found -> {
                    store.delete(applicationScope, id);
                    auditTrail.clientAccountChanged("removed", found.serviceId(), found.id());
                    return true;
                }))
                .orElse(false);
    }

    /** Identificador que nao e numero e identificador que nao existe sao o mesmo caso. */
    private static Optional<Long> identifierOf(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    /**
     * Solicita recuperacao sem revelar se o e-mail existe. Substitui os tokens de recuperacao
     * anteriores. O indice UX_ACCOUNT_CONFIRMATION_PENDING garante um pendente por conta e
     * finalidade; somente esse conflito concorrente e ignorado apos rollback.
     */
    public void requestPasswordRecovery(ApplicationCaller caller, String email) {
        ConfirmationToken token = ConfirmationToken.generate();
        try {
            transactions.executeWithoutResult(status -> issueRecovery(caller, email, token));
        } catch (DataIntegrityViolationException failure) {
            if (!AccountConstraintViolation.matches(failure, "UX_ACCOUNT_CONFIRMATION_PENDING")) {
                throw failure;
            }
        }
    }

    /** Envia o token ao endereco cadastrado, encontrado pela comparacao de e-mail no banco. */
    private void issueRecovery(ApplicationCaller caller, String email, ConfirmationToken token) {
        store.findToNotifyByApplicationAndEmail(caller.applicationId(), email)
                .ifPresent(account -> {
                    store.invalidatePendingTokens(account.id(),
                            OutboundMessage.Purpose.PASSWORD_RECOVERY);
                    store.issueToken(account.id(), token,
                            OutboundMessage.Purpose.PASSWORD_RECOVERY, recoveryLifetime);
                    messageDelivery.deliver(new OutboundMessage(account.registeredEmail(),
                            OutboundMessage.Purpose.PASSWORD_RECOVERY, token.clearValue()));
                });
    }

    /**
     * Redefine a senha com token de recuperacao. Valida a senha antes de consumir o token e
     * invalida somente recuperacoes pendentes, preservando a confirmacao de e-mail.
     */
    @Transactional
    public boolean resetPassword(ApplicationCaller caller, String presentedToken,
            String newPassword) {
        if (!PasswordPolicy.violationsOf(newPassword).isEmpty()) {
            return false;
        }
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> store.consumeToken(caller.applicationId(), token,
                        OutboundMessage.Purpose.PASSWORD_RECOVERY))
                .map(accountId -> {
                    store.updatePasswordHash(accountId, passwordEncoder.encode(newPassword));
                    store.invalidatePendingTokens(accountId,
                            OutboundMessage.Purpose.PASSWORD_RECOVERY);
                    store.invalidatePendingEmailChangeTokens(accountId);
                    store.clearEmailChange(accountId);
                    auditTrail.clientCredentialChanged("password_reset",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }

    /**
     * Confirma o e-mail com token valido da mesma aplicacao. Token desconhecido, usado, vencido ou
     * de finalidade incorreta produz a mesma recusa.
     */
    @Transactional
    public boolean confirmEmail(ApplicationCaller caller, String presentedToken) {
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> store.consumeToken(caller.applicationId(), token,
                        OutboundMessage.Purpose.EMAIL_CONFIRMATION))
                .map(accountId -> {
                    store.confirmEmail(accountId);
                    auditTrail.clientCredentialChanged("email_confirmed",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }
}
