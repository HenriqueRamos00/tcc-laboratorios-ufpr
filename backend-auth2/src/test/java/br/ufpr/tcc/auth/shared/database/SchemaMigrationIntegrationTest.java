package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prova que a migracao rodou e que o mapeamento das entidades confere com o esquema que
 * ela produziu. Subir o contexto ja e metade da afirmacao, porque ddl-auto esta em
 * validate: tabela ausente ou tipo divergente derruba a subida.
 */
class SchemaMigrationIntegrationTest extends OracleIntegrationTestBase {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("a migracao cria as quatro tabelas e o mapeamento valida contra elas")
    void migrationCreatesEverySchemaObject() {
        for (String table : new String[] {"APPLICATION", "COMPANY", "CLIENT_ACCOUNT", "ADMINISTRATOR"}) {
            Long rows = jdbcClient.sql("SELECT COUNT(*) FROM " + SCHEMA_PREFIX + table)
                    .query(Long.class).single();

            // A afirmacao e que a tabela existe e responde, nao que esteja vazia: o
            // contentor e reaproveitado pela suite inteira e outras classes gravam nela.
            assertThat(rows).as("tabela %s", table).isNotNull();
        }
    }

    @Test
    @DisplayName("a aplicacao enxerga o esquema pelo usuario restrito, sem ser dona dele")
    void applicationReadsTheSchemaItDoesNotOwn() {
        String connectedUser = jdbcClient.sql("SELECT USER FROM DUAL").query(String.class).single();

        assertThat(connectedUser).isEqualToIgnoringCase(APPLICATION_USERNAME);
    }
}
