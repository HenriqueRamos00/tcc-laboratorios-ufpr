package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccount;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Troca a senha pelo autoatendimento, com a senha atual.
 *
 * Rota: POST /api/self-service/accounts/{accountId}/password, credencial de aplicacao. A senha
 * nova precisa de doze pontos de codigo, mais exigente que os oito do cadastro, alem de
 * maiuscula, minuscula e digito. A troca sobe a versao da credencial, o que derruba as outras
 * sessoes do portal, e invalida os links de recuperacao e a troca de e-mail pendentes.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ChangePasswordUseCase {

    static final int MINIMUM_LENGTH = 12;
    static final int MAXIMUM_LENGTH = 72;

    public enum Outcome {
        CHANGED, NOT_FOUND, CURRENT_PASSWORD_INCORRECT, PASSWORD_REJECTED, RATE_LIMITED
    }

    /** Desfecho da troca; o prazo de espera so vale para RATE_LIMITED. */
    public record Result(Outcome outcome, long retryAfterSeconds) {

        Result(Outcome outcome) {
            this(outcome, 0);
        }
    }

    private final ClientAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final CurrentPasswordVerification currentPasswordVerification;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    public Result execute(ApplicationCaller caller, String accountId, String currentPassword,
            String newPassword) {
        Optional<Long> parsed = Identifiers.parsePositive(accountId);
        if (parsed.isEmpty()) {
            return new Result(Outcome.NOT_FOUND);
        }
        if (newPassword == null || newPassword.length() > MAXIMUM_LENGTH
                || newPassword.codePointCount(0, newPassword.length()) < MINIMUM_LENGTH
                || !PasswordPolicy.violationsOf(newPassword).isEmpty()) {
            return new Result(Outcome.PASSWORD_REJECTED);
        }
        return transactions.execute(status -> {
            Optional<ClientAccount> found = store.lockedAccount(caller.applicationId(), parsed.get())
                    .filter(account -> account.active() && account.emailConfirmed());
            if (found.isEmpty()) {
                return new Result(Outcome.NOT_FOUND);
            }
            ClientAccount account = found.get();
            OffsetDateTime now = OffsetDateTime.now();
            long wait = currentPasswordVerification.secondsUntilNextAttempt(account, now);
            if (wait > 0) {
                return new Result(Outcome.RATE_LIMITED, wait);
            }
            if (!currentPasswordVerification.matches(account, currentPassword, now)) {
                return new Result(Outcome.CURRENT_PASSWORD_INCORRECT);
            }
            account.changePasswordHash(passwordEncoder.encode(newPassword));
            store.invalidatePendingTokens(parsed.get(), OutboundMessage.Purpose.PASSWORD_RECOVERY);
            store.discardPendingEmailChange(parsed.get());
            auditTrail.clientCredentialChanged("password_changed", Long.toString(parsed.get()));
            return new Result(Outcome.CHANGED);
        });
    }
}
