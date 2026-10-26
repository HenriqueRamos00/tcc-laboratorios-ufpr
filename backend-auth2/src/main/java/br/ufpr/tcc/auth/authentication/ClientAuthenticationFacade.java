package br.ufpr.tcc.auth.authentication;

import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import org.springframework.stereotype.Service;

/**
 * O que a camada web enxerga da autenticacao.
 *
 * Ela existe para que o controlador nao receba o tipo de desfecho inteiro: com os tres
 * casos na mao, mapear cada um para uma resposta diferente vira a coisa natural a fazer, e
 * a diferenca entre eles e exatamente o que nao pode vazar.
 *
 * O desfecho volta no proprio retorno, e nao em estado guardado por linha de execucao:
 * valor preso a thread de um pool sobrevive a requisicao e passa a responder a pergunta da
 * seguinte.
 */
@Service
public class ClientAuthenticationFacade {

    private final AuthenticateClient authenticateClient;

    ClientAuthenticationFacade(AuthenticateClient authenticateClient) {
        this.authenticateClient = authenticateClient;
    }

    public LoginDecision authenticate(ApplicationCaller caller, String email, String password) {
        ClientAuthentication outcome = authenticateClient.authenticate(caller, email, password);
        if (outcome instanceof ClientAuthentication.Authenticated authenticated) {
            return new LoginDecision(authenticated.identity(), 0L);
        }
        if (outcome instanceof ClientAuthentication.Locked locked) {
            return new LoginDecision(null, locked.retryAfterSeconds());
        }
        return new LoginDecision(null, 0L);
    }
}
