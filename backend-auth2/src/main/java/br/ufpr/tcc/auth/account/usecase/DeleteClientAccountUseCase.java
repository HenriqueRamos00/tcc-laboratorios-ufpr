package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclui a conta, seus links pendentes e seus vinculos com empresa.
 *
 * Rota: DELETE /api/users/{id}, credencial administrativa. Devolve falso quando a conta nao
 * existe no escopo de quem chama.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class DeleteClientAccountUseCase {

    private final ClientAccountStore store;
    private final AuditTrail auditTrail;

    @Transactional
    public boolean execute(Optional<Long> applicationScope, String accountId) {
        return Identifiers.parse(accountId)
                .flatMap(id -> store.findWithinScope(applicationScope, id).map(found -> {
                    store.delete(applicationScope, id);
                    auditTrail.clientAccountChanged("removed", found.serviceId(), found.id());
                    return true;
                }))
                .orElse(false);
    }
}
