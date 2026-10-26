package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.authentication.ClientCredentialDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Le a credencial guardada de um cliente, sempre dentro de uma aplicacao.
 *
 * Nao existe aqui metodo que busque so por e-mail. A assinatura exige o escopo, e com isso
 * consultar conta de outra aplicacao deixa de ser descuido possivel e passa a ser codigo
 * que nao compila.
 */
@Component
class ClientAccountDirectory implements ClientCredentialDirectory {

    private static final String FIND = """
            SELECT account.ID, account.NAME, account.EMAIL, account.PASSWORD_HASH,
                   account.EMAIL_CONFIRMED, account.ACTIVE, company.CNPJ
              FROM CLIENT_ACCOUNT account
              LEFT JOIN COMPANY company
                ON company.APPLICATION_ID = account.APPLICATION_ID
               AND company.ID = account.COMPANY_ID
             WHERE account.APPLICATION_ID = :applicationId
               AND account.EMAIL_NORMALIZED = LOWER(:email)
            """;

    private final JdbcClient jdbcClient;

    ClientAccountDirectory(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<StoredClientCredentialView> findByApplicationAndEmail(
            long applicationId, String email) {
        return jdbcClient.sql(FIND)
                .param("applicationId", applicationId)
                .param("email", ClientAccountRegistry.withoutSurroundingSpace(email))
                .query((resultSet, rowNumber) -> {
                    String cnpj = resultSet.getString("CNPJ");
                    return new StoredClientCredentialView(
                            resultSet.getLong("ID"), resultSet.getString("NAME"),
                            resultSet.getString("EMAIL"), resultSet.getString("PASSWORD_HASH"),
                            resultSet.getInt("EMAIL_CONFIRMED") == 1,
                            resultSet.getInt("ACTIVE") == 1,
                            cnpj == null ? List.of() : List.of(cnpj));
                })
                .optional();
    }
}
