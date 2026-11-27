package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Le uma conta no escopo de quem chama.
 *
 * Rota: GET /api/users/{id}, credencial administrativa ou de aplicacao. Conta de outra
 * aplicacao responde como inexistente, e nao como proibida.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GetClientAccountUseCase {

    private final ClientAccountStore store;

    public Optional<AccountSummary> execute(Optional<Long> applicationScope, String accountId) {
        return Identifiers.parse(accountId)
                .flatMap(id -> store.findWithinScope(applicationScope, id))
                .map(AccountSummary::of);
    }
}
