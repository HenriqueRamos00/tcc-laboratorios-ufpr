package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.AdministrativeToken;
import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import br.ufpr.tcc.auth.shared.security.AdministratorCaller;
import br.ufpr.tcc.auth.shared.security.AdministratorCallerDirectory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Abre e reconhece sessoes administrativas.
 *
 * A consulta exige de uma vez que o token exista, nao esteja revogado, nao esteja vencido e
 * pertenca a um administrador ativo. As quatro condicoes vivem no mesmo WHERE porque,
 * separadas, cada uma viraria um caminho com resposta propria.
 */
@Component
class AdministrativeSessionDirectory implements AdministratorCallerDirectory {

    private static final String RESOLVE = """
            SELECT administrator.ID AS ADMINISTRATOR_ID, administrator.NAME AS ADMINISTRATOR_NAME
              FROM ADMINISTRATIVE_SESSION opened
              JOIN ADMINISTRATOR administrator ON administrator.ID = opened.ADMINISTRATOR_ID
             WHERE opened.TOKEN_DIGEST = :digest
               AND opened.REVOKED_AT IS NULL
               AND opened.EXPIRES_AT > SYSTIMESTAMP
               AND administrator.ACTIVE = TRUE
            """;

    private final JdbcClient jdbcClient;
    private final IdentifierFactory identifiers;

    AdministrativeSessionDirectory(JdbcClient jdbcClient, IdentifierFactory identifiers) {
        this.jdbcClient = jdbcClient;
        this.identifiers = identifiers;
    }

    @Override
    public Optional<AdministratorCaller> resolve(String presentedToken) {
        return AdministrativeToken.fromPresentedValue(presentedToken)
                .flatMap(token -> jdbcClient.sql(RESOLVE)
                        .param("digest", token.digest().asBytes())
                        .query((resultSet, rowNumber) -> new AdministratorCaller(
                                resultSet.getLong("ADMINISTRATOR_ID"),
                                resultSet.getString("ADMINISTRATOR_NAME")))
                        .optional());
    }

    /** Abre a sessao e devolve o token em claro, que so existe neste instante. */
    AdministrativeToken open(long administratorId, Duration lifetime) {
        AdministrativeToken token = AdministrativeToken.generate();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("INSERT INTO ADMINISTRATIVE_SESSION (ID, ADMINISTRATOR_ID, TOKEN_DIGEST,"
                        + " CREATED_AT, EXPIRES_AT) VALUES (:id, :administratorId, :digest, :now,"
                        + " :expiresAt)")
                .param("id", identifiers.nextIdentifier()).param("administratorId", administratorId)
                .param("digest", token.digest().asBytes()).param("now", now)
                .param("expiresAt", now.plus(lifetime)).update();
        return token;
    }

    /**
     * Fecha as sessoes abertas de um administrador sem apagar a linha.
     *
     * Usada na troca de senha: a senha muda porque a conta pode estar comprometida, e deixar
     * a sessao anterior de pe devolveria o acesso a quem a troca deveria ter tirado. A linha
     * fica, com a hora da revogacao, porque ela e trilha de auditoria.
     */
    void revokeAllOf(long administratorId) {
        jdbcClient.sql("UPDATE ADMINISTRATIVE_SESSION SET REVOKED_AT = :now"
                        + " WHERE ADMINISTRATOR_ID = :administratorId AND REVOKED_AT IS NULL")
                .param("administratorId", administratorId).param("now", OffsetDateTime.now())
                .update();
    }

    /**
     * Apaga as sessoes de um administrador que esta sendo excluido.
     *
     * Aqui a linha nao pode ficar, ao contrario da revogacao: a chave estrangeira aponta
     * para o administrador, e a exclusao dele falharia enquanto existir sessao apontando.
     */
    void deleteAllOf(long administratorId) {
        jdbcClient.sql("DELETE FROM ADMINISTRATIVE_SESSION WHERE ADMINISTRATOR_ID"
                        + " = :administratorId")
                .param("administratorId", administratorId).update();
    }
}
