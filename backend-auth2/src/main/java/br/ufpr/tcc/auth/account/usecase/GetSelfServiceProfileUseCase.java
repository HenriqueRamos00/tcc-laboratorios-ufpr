package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Le o perfil da conta para o autoatendimento.
 *
 * Rota: GET /api/self-service/accounts/{accountId}/profile, credencial de aplicacao. So conta
 * ativa e confirmada da propria aplicacao tem perfil; as demais respondem como inexistentes.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GetSelfServiceProfileUseCase {

    private final ClientAccountStore store;
    private final SelfServiceProfiles profiles;

    public Optional<SelfServiceProfile> execute(ApplicationCaller caller, String accountId) {
        return Identifiers.parsePositive(accountId)
                .flatMap(id -> store.selfServiceAccount(caller.applicationId(), id, false))
                .filter(account -> account.active() && account.emailConfirmed())
                .map(profiles::of);
    }
}
