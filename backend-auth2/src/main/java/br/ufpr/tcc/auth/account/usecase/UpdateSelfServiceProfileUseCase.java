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
 * Altera o nome da conta pelo autoatendimento.
 *
 * Rota: PATCH /api/self-service/accounts/{accountId}/profile, credencial de aplicacao. O nome
 * e o unico campo editavel; o controlador recusa qualquer outro campo antes de chegar aqui.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class UpdateSelfServiceProfileUseCase {

    private final ClientAccountStore store;
    private final SelfServiceProfiles profiles;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    public Optional<SelfServiceProfile> execute(ApplicationCaller caller, String accountId,
            String name) {
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
                    .map(profiles::of);
        });
    }
}
