package br.ufpr.tcc.auth.credential;

import br.ufpr.tcc.auth.credential.model.MalformedServiceTokenException;
import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import br.ufpr.tcc.auth.shared.security.ApplicationCallerDirectory;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Encontra a aplicacao dona de um token apresentado.
 *
 * A consulta exige, de uma vez, que o token exista, nao esteja revogado, nao esteja
 * vencido e pertenca a uma aplicacao ativa. As quatro condicoes vivem no mesmo WHERE de
 * proposito: separadas em if, cada uma viraria um caminho com resposta propria, e a
 * diferenca entre elas e exatamente o que nao pode vazar.
 *
 * A busca e pelo digest, com restricao unica na coluna, porque o token nao carrega
 * identificador embutido.
 */
@Component
class ApplicationTokenDirectory implements ApplicationCallerDirectory {

    private static final String RESOLVE = """
            SELECT application.ID AS APPLICATION_ID, application.CODE AS APPLICATION_CODE
              FROM APPLICATION_TOKEN token
              JOIN APPLICATION application ON application.ID = token.APPLICATION_ID
             WHERE token.TOKEN_DIGEST = :digest
               AND token.REVOKED_AT IS NULL
               AND (token.EXPIRES_AT IS NULL OR token.EXPIRES_AT > SYSTIMESTAMP)
               AND application.ACTIVE = TRUE
            """;

    private final JdbcClient jdbcClient;

    ApplicationTokenDirectory(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ApplicationCaller> resolve(String presentedToken) {
        ServiceToken token;
        try {
            token = ServiceToken.fromPresentedValue(presentedToken);
        } catch (MalformedServiceTokenException malformed) {
            return Optional.empty();
        }
        return jdbcClient.sql(RESOLVE)
                .param("digest", token.digest().asBytes())
                .query((resultSet, rowNumber) -> new ApplicationCaller(
                        resultSet.getLong("APPLICATION_ID"), resultSet.getString("APPLICATION_CODE")))
                .optional();
    }
}
