package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
