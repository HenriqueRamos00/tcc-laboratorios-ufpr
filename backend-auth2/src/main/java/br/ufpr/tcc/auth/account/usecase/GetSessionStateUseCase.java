package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Le o estado da conta que decide se a sessao do portal continua valendo.
 *
 * Rota: GET /api/self-service/accounts/{accountId}/session-state, credencial de aplicacao. A
 * API do portal compara a versao da credencial com a gravada no JWT a cada requisicao: troca
 * de senha ou de e-mail sobe a versao, e a conta desativada ou nao confirmada perde a sessao.
 * Por isso a resposta sai tambem para a conta que perdeu o acesso.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GetSessionStateUseCase {

    public record SessionState(String id, boolean active, boolean emailConfirmed,
            long credentialVersion) {
    }

    private final ClientAccountStore store;

    public Optional<SessionState> execute(ApplicationCaller caller, String accountId) {
        return Identifiers.parsePositive(accountId)
                .flatMap(id -> store.selfServiceAccount(caller.applicationId(), id, false))
                .map(account -> new SessionState(Long.toString(account.id()), account.active(),
                        account.emailConfirmed(), account.credentialVersion()));
    }
}
