package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    CompanyRegistry(JdbcClient jdbcClient, IdentifierFactory identifiers) {
        this.jdbcClient = jdbcClient;
        this.identifiers = identifiers;
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

    public boolean cnpjAlreadyUsedIn(long applicationId, String cnpj) {
        return jdbcClient.sql("SELECT COUNT(*) FROM COMPANY"
                        + " WHERE APPLICATION_ID = :applicationId AND CNPJ = :cnpj")
                .param("applicationId", applicationId).param("cnpj", cnpj)
                .query(Long.class).single() > 0L;
    }

    @Transactional
    public CompanyView register(long applicationId, String name, String cnpj) {
        long id = identifiers.nextIdentifier();
        jdbcClient.sql("INSERT INTO COMPANY (ID, APPLICATION_ID, NAME, CNPJ, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :name, :cnpj, :now, :now)")
                .param("id", id).param("applicationId", applicationId).param("name", name)
                .param("cnpj", cnpj).param("now", OffsetDateTime.now()).update();
        return new CompanyView(Long.toString(id), name, cnpj, Long.toString(applicationId));
    }

    @Transactional
    public Optional<CompanyView> update(long applicationId, long companyId, String name, String cnpj) {
        if (find(applicationId, companyId).isEmpty()) {
            return Optional.empty();
        }
        jdbcClient.sql("UPDATE COMPANY SET NAME = :name, CNPJ = :cnpj, UPDATED_AT = :now"
                        + " WHERE ID = :id AND APPLICATION_ID = :applicationId")
                .param("id", companyId).param("applicationId", applicationId).param("name", name)
                .param("cnpj", cnpj).param("now", OffsetDateTime.now()).update();
        return find(applicationId, companyId);
    }

    /**
     * Remove a empresa dentro da aplicacao dela. Conta vinculada impede a remocao pela
     * chave estrangeira, e isso e o comportamento desejado: apagar a empresa deixaria a
     * conta apontando para o nada.
     */
    @Transactional
    public boolean delete(long applicationId, long companyId) {
        return jdbcClient.sql("DELETE FROM COMPANY WHERE ID = :id"
                        + " AND APPLICATION_ID = :applicationId")
                .param("id", companyId).param("applicationId", applicationId).update() == 1;
    }
}
