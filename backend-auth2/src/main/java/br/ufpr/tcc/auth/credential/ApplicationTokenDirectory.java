package br.ufpr.tcc.auth.credential;

import br.ufpr.tcc.auth.application.ApplicationTokenRepository;
import br.ufpr.tcc.auth.credential.model.MalformedServiceTokenException;
import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import br.ufpr.tcc.auth.shared.security.ApplicationCallerDirectory;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reconhece a aplicacao dona de um token apresentado.
 *
 * O que esta classe faz e ler o valor apresentado e recusar o que nem tem a forma de um
 * token, antes de perguntar ao banco. A consulta em si pertence a fatia que guarda a tabela,
 * e e la que as quatro condicoes de validade vivem juntas no mesmo WHERE: token existente,
 * nao revogado, nao vencido e de aplicacao ativa.
 *
 * Token malformado nao vira consulta. Isso nao e otimizacao: valor de formato invalido tem o
 * mesmo desfecho de token desconhecido, e perguntar ao banco por ele seria transformar
 * varredura em carga de consulta.
 */
@Component
class ApplicationTokenDirectory implements ApplicationCallerDirectory {

    private final ApplicationTokenRepository tokens;

    ApplicationTokenDirectory(ApplicationTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public Optional<ApplicationCaller> resolve(String presentedToken) {
        ServiceToken token;
        try {
            token = ServiceToken.fromPresentedValue(presentedToken);
        } catch (MalformedServiceTokenException malformed) {
            return Optional.empty();
        }
        return tokens.resolveActiveCaller(token.digest().asBytes());
    }
}
