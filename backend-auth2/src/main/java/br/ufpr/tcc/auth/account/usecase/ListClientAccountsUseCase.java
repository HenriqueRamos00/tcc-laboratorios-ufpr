package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Lista as contas no escopo de quem chama.
 *
 * Rota: GET /api/users, credencial administrativa ou de aplicacao. A aplicacao ve apenas as
 * proprias contas; o administrador, com escopo vazio, ve as de todas as aplicacoes.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ListClientAccountsUseCase {

    private final ClientAccountStore store;

    public List<AccountSummary> execute(Optional<Long> applicationScope) {
        return store.list(applicationScope).stream().map(AccountSummary::of).toList();
    }
}
