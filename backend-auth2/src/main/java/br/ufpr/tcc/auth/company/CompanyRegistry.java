package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de empresa dentro de uma aplicacao.
 *
 * O escopo entra em toda assinatura e nao e opcional. Repositorio que aceite buscar empresa
 * so por identificador deixa o filtro por aplicacao virar escolha de quem chama, e e assim
 * que nasce uma consulta que enxerga cadastro alheio.
 */
@Service
public class CompanyRegistry {

    private static final String SELECT = "SELECT ID, NAME, CNPJ, APPLICATION_ID FROM COMPANY";

    public record CompanyView(String id, String name, String cnpj, String serviceId) {
    }

    private final JdbcClient jdbcClient;
    private final IdentifierFactory identifiers;
    private final AuditTrail auditTrail;

    CompanyRegistry(JdbcClient jdbcClient, IdentifierFactory identifiers,
            AuditTrail auditTrail) {
        this.jdbcClient = jdbcClient;
        this.identifiers = identifiers;
        this.auditTrail = auditTrail;
    }

    private static CompanyView toView(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new CompanyView(Long.toString(resultSet.getLong("ID")), resultSet.getString("NAME"),
                resultSet.getString("CNPJ"), Long.toString(resultSet.getLong("APPLICATION_ID")));
    }

    public List<CompanyView> list(long applicationId) {
        return jdbcClient.sql(SELECT + " WHERE APPLICATION_ID = :applicationId"
                        + " ORDER BY CREATED_AT DESC")
                .param("applicationId", applicationId)
                .query((resultSet, rowNumber) -> toView(resultSet)).list();
    }

    public Optional<CompanyView> find(long applicationId, long companyId) {
        return jdbcClient.sql(SELECT + " WHERE ID = :id AND APPLICATION_ID = :applicationId")
                .param("id", companyId).param("applicationId", applicationId)
                .query((resultSet, rowNumber) -> toView(resultSet)).optional();
    }

    /** Desfecho do cadastro, com a empresa como ela ficou quando houve cadastro. */
    public record Registration(Outcome outcome, CompanyView company) {

        public enum Outcome { CREATED, CNPJ_ALREADY_REGISTERED }
    }

    /** Desfecho da alteracao, com o conflito de CNPJ separado de nao encontrado. */
    public enum UpdateOutcome { CHANGED, NOT_FOUND, CNPJ_ALREADY_REGISTERED }

    private boolean cnpjAlreadyUsedIn(long applicationId, String cnpj) {
        return jdbcClient.sql("SELECT COUNT(*) FROM COMPANY"
                        + " WHERE APPLICATION_ID = :applicationId AND CNPJ = :cnpj")
                .param("applicationId", applicationId).param("cnpj", cnpj)
                .query(Long.class).single() > 0L;
    }

    /**
     * A consulta de CNPJ repetido nao substitui a restricao unica do banco: entre consultar
     * e gravar existe janela, e dois cadastros simultaneos do mesmo CNPJ passam os dois pela
     * consulta. Quem recusa o segundo e a restricao, e sem traduzir essa recusa o desfecho
     * seria erro de servidor no lugar do conflito que a matriz manda.
     *
     * A consulta vive aqui e nao no controlador por dois motivos. O controlador nao decide
     * regra de negocio, e a consulta fora da transacao da gravacao abre a janela ainda mais.
     */
    @Transactional
    public Registration register(long applicationId, String name, String cnpj) {
        if (cnpjAlreadyUsedIn(applicationId, cnpj)) {
            return new Registration(Registration.Outcome.CNPJ_ALREADY_REGISTERED, null);
        }
        long id = identifiers.nextIdentifier();
        try {
            jdbcClient.sql("INSERT INTO COMPANY (ID, APPLICATION_ID, NAME, CNPJ, CREATED_AT,"
                            + " UPDATED_AT) VALUES (:id, :applicationId, :name, :cnpj, :now, :now)")
                    .param("id", id).param("applicationId", applicationId).param("name", name)
                    .param("cnpj", cnpj).param("now", OffsetDateTime.now()).update();
        } catch (DuplicateKeyException cnpjAlreadyRegistered) {
            return new Registration(Registration.Outcome.CNPJ_ALREADY_REGISTERED, null);
        }
        auditTrail.companyChanged("registered", Long.toString(applicationId), Long.toString(id));
        return new Registration(Registration.Outcome.CREATED,
                new CompanyView(Long.toString(id), name, cnpj, Long.toString(applicationId)));
    }

    /**
     * Mover o CNPJ para um que ja existe na mesma aplicacao e conflito, nao erro de
     * servidor. Aqui nao havia nem a consulta previa que o cadastro tem: a alteracao chegava
     * ao banco, a restricao unica recusava, e ninguem traduzia a recusa. Medido antes desta
     * traducao, com uma requisicao sequencial: ORA-00001 subia como DuplicateKeyException e a
     * rota respondia erro de servidor, que no consumidor vira fonte indisponivel.
     */
    @Transactional
    public UpdateOutcome update(long applicationId, long companyId, String name, String cnpj) {
        if (find(applicationId, companyId).isEmpty()) {
            return UpdateOutcome.NOT_FOUND;
        }
        try {
            jdbcClient.sql("UPDATE COMPANY SET NAME = :name, CNPJ = :cnpj, UPDATED_AT = :now"
                            + " WHERE ID = :id AND APPLICATION_ID = :applicationId")
                    .param("id", companyId).param("applicationId", applicationId)
                    .param("name", name).param("cnpj", cnpj)
                    .param("now", OffsetDateTime.now()).update();
        } catch (DuplicateKeyException cnpjAlreadyRegistered) {
            return UpdateOutcome.CNPJ_ALREADY_REGISTERED;
        }
        auditTrail.companyChanged("changed", Long.toString(applicationId),
                Long.toString(companyId));
        return UpdateOutcome.CHANGED;
    }

    /**
     * Remove a empresa dentro da aplicacao dela. Conta vinculada impede a remocao pela
     * chave estrangeira, e isso e o comportamento desejado: apagar a empresa deixaria a
     * conta apontando para o nada.
     */
    @Transactional
    public boolean delete(long applicationId, long companyId) {
        boolean removed = jdbcClient.sql("DELETE FROM COMPANY WHERE ID = :id"
                        + " AND APPLICATION_ID = :applicationId")
                .param("id", companyId).param("applicationId", applicationId).update() == 1;
        if (removed) {
            auditTrail.companyChanged("removed", Long.toString(applicationId),
                    Long.toString(companyId));
        }
        return removed;
    }
}
