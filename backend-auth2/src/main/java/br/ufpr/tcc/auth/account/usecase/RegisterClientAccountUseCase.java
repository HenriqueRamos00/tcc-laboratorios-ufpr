package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.AccountConstraintViolation;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastra conta de cliente numa aplicacao, com as empresas que ela representa.
 *
 * Rota: POST /api/users, credencial administrativa.
 *
 * A conta nasce ativa e com e-mail ainda nao confirmado, e recebe o link de confirmacao.
 * Desfecho diferente de CREATED desfaz tudo: nenhuma conta fica gravada e nenhuma mensagem
 * sai. A transacao e aberta pelo modelo, e nao por anotacao, para que a violacao da
 * restricao de e-mail seja traduzida depois do rollback.
 */
@Service
public class RegisterClientAccountUseCase {

    /** Desfecho do cadastro, com a conta criada quando houve criacao. */
    public record Result(Outcome outcome, AccountSummary account) {

        public enum Outcome {
            CREATED, PASSWORD_REJECTED, EMAIL_TAKEN, SERVICE_NOT_FOUND, SERVICE_INACTIVE,
            COMPANY_NOT_FOUND
        }

        static Result refused(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    private final ClientAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final MessageDelivery messageDelivery;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;
    private final Duration confirmationLifetime;

    RegisterClientAccountUseCase(ClientAccountStore store, PasswordEncoder passwordEncoder,
            MessageDelivery messageDelivery, AuditTrail auditTrail,
            TransactionTemplate transactions,
            @Value("${auth.confirmation.lifetime}") Duration confirmationLifetime) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
        this.confirmationLifetime = confirmationLifetime;
    }

    /**
     * Valida e calcula o hash da senha antes de abrir a transacao. Remove empresas repetidas e
     * desfaz o cadastro se alguma referencia for invalida.
     */
    public Result execute(long applicationId, String name, String email, String password,
            List<Long> companyIds) {
        if (!PasswordPolicy.violationsOf(password).isEmpty()) {
            return Result.refused(Result.Outcome.PASSWORD_REJECTED);
        }
        String passwordHash = passwordEncoder.encode(password);
        ConfirmationToken token = ConfirmationToken.generate();
        Set<Long> companies = new LinkedHashSet<>(companyIds);
        try {
            return transactions.execute(status -> {
                Result result =
                        registerInTransaction(applicationId, name, email, passwordHash, token, companies);
                if (result.outcome() != Result.Outcome.CREATED) {
                    status.setRollbackOnly();
                }
                return result;
            });
        } catch (DataIntegrityViolationException failure) {
            if (AccountConstraintViolation.matches(failure, "UK_CLIENT_ACCOUNT_EMAIL")) {
                return Result.refused(Result.Outcome.EMAIL_TAKEN);
            }
            throw failure;
        }
    }

    /**
     * Executa o cadastro na transacao. A restricao unica garante exclusividade do e-mail mesmo com
     * pedidos simultaneos. Os vinculos sao validados antes de emitir o token e solicitar a
     * mensagem.
     */
    private Result registerInTransaction(long applicationId, String name, String email,
            String passwordHash, ConfirmationToken token, Set<Long> companyIds) {
        Optional<Boolean> applicationActive = store.applicationSituation(applicationId);
        if (applicationActive.isEmpty()) {
            return Result.refused(Result.Outcome.SERVICE_NOT_FOUND);
        }
        if (!applicationActive.get()) {
            return Result.refused(Result.Outcome.SERVICE_INACTIVE);
        }
        if (store.emailAlreadyUsedIn(applicationId, email)) {
            return Result.refused(Result.Outcome.EMAIL_TAKEN);
        }
        long accountId = store.insert(applicationId, name, email, passwordHash);
        if (!store.replaceCompanies(accountId, companyIds)) {
            return Result.refused(Result.Outcome.COMPANY_NOT_FOUND);
        }
        store.issueToken(accountId, token,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, confirmationLifetime);
        messageDelivery.deliver(new OutboundMessage(email,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, token.clearValue()));
        auditTrail.clientAccountChanged("created", Long.toString(applicationId),
                Long.toString(accountId));
        return store.findWithinScope(Optional.empty(), accountId)
                .map(created -> new Result(Result.Outcome.CREATED, AccountSummary.of(created)))
                .orElseThrow(() -> new IllegalStateException(
                        "Conta recem gravada nao foi encontrada na mesma transacao"));
    }
}
