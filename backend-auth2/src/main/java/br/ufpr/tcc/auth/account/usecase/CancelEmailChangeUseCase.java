package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccount;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancela a troca de e-mail pendente.
 *
 * Rota: DELETE /api/self-service/accounts/{accountId}/email-change, credencial de aplicacao.
 * Os dois links do pedido deixam de valer. Devolve falso quando a conta nao e da aplicacao.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CancelEmailChangeUseCase {

    private final ClientAccountStore store;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    public boolean execute(ApplicationCaller caller, String accountId) {
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
            store.discardPendingEmailChange(account.id());
            auditTrail.clientCredentialChanged("email_change_cancelled", Long.toString(account.id()));
            return true;
        });
    }
}
