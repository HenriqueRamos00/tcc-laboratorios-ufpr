package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.AdministrativeToken;
import br.ufpr.tcc.auth.shared.security.AdministratorCaller;
import br.ufpr.tcc.auth.shared.security.AdministratorCallerDirectory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Abre e reconhece sessoes administrativas.
 *
 * O que esta classe faz e ler o valor apresentado e recusar o que nem tem a forma de um
 * token, antes de perguntar ao banco. As quatro condicoes de validade vivem juntas na
 * consulta do repositorio: sessao existente, nao revogada, nao vencida e de administrador
 * ativo.
 */
@Component
class AdministrativeSessionDirectory implements AdministratorCallerDirectory {

    private final AdministrativeSessionRepository sessions;

    AdministrativeSessionDirectory(AdministrativeSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<AdministratorCaller> resolve(String presentedToken) {
        return AdministrativeToken.fromPresentedValue(presentedToken)
                .flatMap(token -> sessions.resolveActiveCaller(token.digest().asBytes()));
    }

    /** Abre a sessao e devolve o token em claro, que so existe neste instante. */
    AdministrativeToken open(long administratorId, Duration lifetime) {
        AdministrativeToken token = AdministrativeToken.generate();
        sessions.save(AdministrativeSession.openedFor(administratorId, token, lifetime));
        return token;
    }

    void revokeAllOf(long administratorId) {
        sessions.revokeOpenSessionsOf(administratorId, OffsetDateTime.now());
    }

    void deleteAllOf(long administratorId) {
        sessions.deleteSessionsOf(administratorId);
    }
}
