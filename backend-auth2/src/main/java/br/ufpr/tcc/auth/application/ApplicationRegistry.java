package br.ufpr.tcc.auth.application;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import org.springframework.dao.DuplicateKeyException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de aplicacoes e emissao dos tokens delas.
 *
 * O token emitido volta em claro uma unica vez, no corpo da resposta da emissao. O que
 * fica guardado e o digest: quem perder o valor pede outro, e ninguem, nem o
 * administrador, consegue recuperar o anterior.
 */
@Service
public class ApplicationRegistry {

    private final JdbcClient jdbcClient;
    private final IdentifierFactory identifiers;
    private final AuditTrail auditTrail;

    ApplicationRegistry(JdbcClient jdbcClient, IdentifierFactory identifiers,
            AuditTrail auditTrail) {
        this.jdbcClient = jdbcClient;
        this.identifiers = identifiers;
        this.auditTrail = auditTrail;
    }

    public record RegisteredApplicationView(String id, String name, String code, boolean active) {
    }

    /** Desfecho do cadastro de aplicacao, com o conflito de codigo separado do sucesso. */
    public record Registration(Outcome outcome, RegisteredApplicationView application) {

        public enum Outcome { CREATED, CODE_ALREADY_REGISTERED }
    }

    /**
     * O codigo e unico no banco, e o segundo cadastro com o mesmo codigo precisa responder
     * conflito. Medido antes desta traducao: a violacao da restricao subia como erro de
     * servidor, que nao esta na matriz de codigos e que no consumidor vira fonte
     * indisponivel. O 409 e aceitavel aqui porque quem chama ja pode ler a lista inteira de
     * aplicacoes na rota ao lado, entao a resposta nao conta nada novo.
     */
    @Transactional
    public Registration register(String name, String code) {
        long id = identifiers.nextIdentifier();
        try {
            jdbcClient.sql("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT,"
                            + " UPDATED_AT) VALUES (:id, :code, :name, TRUE, :now, :now)")
                    .param("id", id).param("code", code).param("name", name)
                    .param("now", OffsetDateTime.now()).update();
        } catch (DuplicateKeyException codeAlreadyRegistered) {
            return new Registration(Registration.Outcome.CODE_ALREADY_REGISTERED, null);
        }
        auditTrail.applicationChanged("registered", Long.toString(id));
        return new Registration(Registration.Outcome.CREATED,
                new RegisteredApplicationView(Long.toString(id), name, code, true));
    }

    public record IssuedTokenView(String accessToken, String id, String name, String serviceId,
            OffsetDateTime createdAt, OffsetDateTime expiresAt) {
    }

    @Transactional
    public IssuedTokenView issueToken(long applicationId, String name, Duration lifetime) {
        ServiceToken token = ServiceToken.generate();
        long id = identifiers.nextIdentifier();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = lifetime == null ? null : now.plus(lifetime);
        jdbcClient.sql("INSERT INTO APPLICATION_TOKEN (ID, APPLICATION_ID, NAME, TOKEN_DIGEST,"
                        + " CREATED_AT, EXPIRES_AT) VALUES (:id, :applicationId, :name, :digest,"
                        + " :now, :expiresAt)")
                .param("id", id).param("applicationId", applicationId).param("name", name)
                .param("digest", token.digest().asBytes()).param("now", now)
                .param("expiresAt", expiresAt).update();
        auditTrail.applicationTokenChanged("issued", Long.toString(applicationId),
                Long.toString(id));
        return new IssuedTokenView(token.clearValue(), Long.toString(id), name,
                Long.toString(applicationId), now, expiresAt);
    }

    /** Metadado do token. O segredo nao aparece aqui, porque ele nao existe mais guardado. */
    public record TokenView(String id, String name, String serviceId, OffsetDateTime createdAt,
            OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    }

    public java.util.List<TokenView> listTokens(long applicationId) {
        return jdbcClient.sql("SELECT ID, NAME, APPLICATION_ID, CREATED_AT, EXPIRES_AT, REVOKED_AT"
                        + " FROM APPLICATION_TOKEN WHERE APPLICATION_ID = :applicationId"
                        + " ORDER BY CREATED_AT DESC")
                .param("applicationId", applicationId)
                .query((resultSet, rowNumber) -> new TokenView(
                        Long.toString(resultSet.getLong("ID")), resultSet.getString("NAME"),
                        Long.toString(resultSet.getLong("APPLICATION_ID")),
                        resultSet.getObject("CREATED_AT", OffsetDateTime.class),
                        resultSet.getObject("EXPIRES_AT", OffsetDateTime.class),
                        resultSet.getObject("REVOKED_AT", OffsetDateTime.class)))
                .list();
    }

    /**
     * Revoga o token dentro da aplicacao dele.
     *
     * A existencia e conferida com o escopo junto, entao token de outra aplicacao e token
     * inventado produzem a mesma resposta: 403 ali confirmaria que o token existe em
     * algum lugar.
     *
     * A marcacao so acontece se ainda nao tiver acontecido, para preservar o instante da
     * primeira revogacao, mas a resposta e a mesma nas duas chamadas: revogar o que ja
     * esta revogado e pedir o estado que ja vale.
     */
    @Transactional
    public boolean revokeToken(long applicationId, long tokenId) {
        boolean belongsToApplication = jdbcClient.sql("SELECT COUNT(*) FROM APPLICATION_TOKEN"
                        + " WHERE ID = :tokenId AND APPLICATION_ID = :applicationId")
                .param("tokenId", tokenId).param("applicationId", applicationId)
                .query(Long.class).single() > 0L;
        if (!belongsToApplication) {
            return false;
        }
        jdbcClient.sql("UPDATE APPLICATION_TOKEN SET REVOKED_AT = SYSTIMESTAMP"
                        + " WHERE ID = :tokenId AND APPLICATION_ID = :applicationId"
                        + " AND REVOKED_AT IS NULL")
                .param("tokenId", tokenId).param("applicationId", applicationId).update();
        auditTrail.applicationTokenChanged("revoked", Long.toString(applicationId),
                Long.toString(tokenId));
        return true;
    }

    public java.util.List<RegisteredApplicationView> list() {
        return jdbcClient.sql("SELECT ID, NAME, CODE, ACTIVE FROM APPLICATION ORDER BY CREATED_AT DESC")
                .query((resultSet, rowNumber) -> new RegisteredApplicationView(
                        Long.toString(resultSet.getLong("ID")), resultSet.getString("NAME"),
                        resultSet.getString("CODE"), resultSet.getBoolean("ACTIVE")))
                .list();
    }

    public java.util.Optional<RegisteredApplicationView> find(long applicationId) {
        return jdbcClient.sql("SELECT ID, NAME, CODE, ACTIVE FROM APPLICATION WHERE ID = :id")
                .param("id", applicationId)
                .query((resultSet, rowNumber) -> new RegisteredApplicationView(
                        Long.toString(resultSet.getLong("ID")), resultSet.getString("NAME"),
                        resultSet.getString("CODE"), resultSet.getBoolean("ACTIVE")))
                .optional();
    }

    /** Desfecho da alteracao, com o conflito de codigo separado de nao encontrado. */
    public enum UpdateOutcome { CHANGED, NOT_FOUND, CODE_ALREADY_REGISTERED }

    @Transactional
    public UpdateOutcome update(long applicationId, String name, String code, boolean active) {
        boolean changed;
        try {
            changed = jdbcClient.sql("UPDATE APPLICATION SET NAME = :name, CODE = :code,"
                            + " ACTIVE = :active, UPDATED_AT = :now WHERE ID = :id")
                    .param("id", applicationId).param("name", name).param("code", code)
                    .param("active", active).param("now", OffsetDateTime.now()).update() == 1;
        } catch (DuplicateKeyException codeAlreadyRegistered) {
            return UpdateOutcome.CODE_ALREADY_REGISTERED;
        }
        if (!changed) {
            return UpdateOutcome.NOT_FOUND;
        }
        auditTrail.applicationChanged("changed", Long.toString(applicationId));
        return UpdateOutcome.CHANGED;
    }

    /**
     * Excluir aplicacao e desativar, nao apagar.
     *
     * Apagar a linha deixaria conta de cliente e trilha de auditoria apontando para o nada,
     * e a trilha precisa continuar explicavel depois. A desativacao ja corta o acesso no
     * mesmo instante, porque a situacao e lida junto com a credencial.
     */
    @Transactional
    public boolean deactivate(long applicationId) {
        boolean deactivated = jdbcClient.sql("UPDATE APPLICATION SET ACTIVE = FALSE,"
                        + " UPDATED_AT = :now WHERE ID = :id")
                .param("id", applicationId).param("now", OffsetDateTime.now()).update() == 1;
        if (deactivated) {
            auditTrail.applicationChanged("deactivated", Long.toString(applicationId));
        }
        return deactivated;
    }

    public boolean exists(long applicationId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM APPLICATION WHERE ID = :id")
                .param("id", applicationId).query(Long.class).single() > 0L;
    }
}
